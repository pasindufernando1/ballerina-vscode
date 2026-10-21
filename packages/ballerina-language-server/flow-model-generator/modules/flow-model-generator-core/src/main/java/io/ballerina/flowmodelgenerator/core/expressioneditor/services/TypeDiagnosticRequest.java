/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.flowmodelgenerator.core.expressioneditor.services;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.Types;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.flowmodelgenerator.core.expressioneditor.ExpressionEditorContext;
import io.ballerina.modelgenerator.commons.CommonUtils;
import io.ballerina.modelgenerator.commons.ImportPrefixReader;
import io.ballerina.modelgenerator.commons.ModuleInfo;
import io.ballerina.modelgenerator.commons.ModulePrefixContext;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.text.TextEdit;
import io.ballerina.tools.text.TextRange;
import org.ballerinalang.langserver.commons.BallerinaCompilerApi;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.eclipse.lsp4j.Diagnostic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Handles diagnostic requests for type descriptor validation in the expression editor.
 *
 * @see DiagnosticsRequest
 * @since 1.0.0
 */
public class TypeDiagnosticRequest extends DiagnosticsRequest {

    private static final String UNDEFINED_TYPE = "undefined type '%s'";
    private static final String INVALID_SUBTYPE = "expected a subtype of '%s', but found '%s'";
    private static final DiagnosticErrorCode UNKNOWN_TYPE_ERROR_CODE = DiagnosticErrorCode.UNKNOWN_TYPE;
    private static final String VAR_KEYWORD = "var";

    public TypeDiagnosticRequest(ExpressionEditorContext context) {
        super(context);
    }

    @Override
    protected Node getParsedNode(String text) {
        return NodeParser.parseTypeDescriptor(getTrimmedOutput(text));
    }

    @Override
    protected Set<Diagnostic> getSyntaxDiagnostics(ExpressionEditorContext context) {
        String inputExpression = getTrimmedOutput(context.info().expression());
        // Skip validation for 'var' as it is a type inference keyword
        if (isVarType(inputExpression)) {
            return Set.of();
        }
        return super.getSyntaxDiagnostics(context);
    }

    @Override
    protected Set<Diagnostic> getSemanticDiagnostics(ExpressionEditorContext context) {
        Set<Diagnostic> diagnostics = new HashSet<>();
        String inputExpression = getTrimmedOutput(context.info().expression());

        // Skip validation for 'var' as it is a type inference keyword
        if (isVarType(inputExpression)) {
            return diagnostics;
        }

        // Imports are file scoped, so the modules the type relies on are imported to the probed file first
        inputExpression = addRequiredImports(context, inputExpression);

        Optional<SemanticModel> semanticModel =
                context.workspaceManager().semanticModel(context.filePath());
        Optional<Document> document = context.workspaceManager().document(context.filePath());
        if (semanticModel.isEmpty() || document.isEmpty()) {
            return diagnostics;
        }

        // Check for undefined types
        Types types = semanticModel.get().types();
        Optional<TypeSymbol> typeSymbol;
        try {
            typeSymbol = BallerinaCompilerApi.getInstance().getType(types, document.get(), inputExpression);
        } catch (NullPointerException e) {
            // TODO: Tracked with https://github.com/ballerina-platform/ballerina-lang/issues/44347
            // Handle cases where the type descriptor doesn't have a parent context
            // (e.g., anonymous record types like "record {}")
            return diagnostics;
        }
        if (typeSymbol.isEmpty()) {
            String message = String.format(UNDEFINED_TYPE, inputExpression);
            diagnostics.add(CommonUtils.createDiagnostic(message, context.getExpressionLineRange(),
                    UNKNOWN_TYPE_ERROR_CODE));
            return diagnostics;
        }

        // Check if the type is a subtype of the type constraint
        String ballerinaType = context.getProperty().propertyType().ballerinaType();
        if (ballerinaType == null) {
            return diagnostics;
        }
        Optional<TypeSymbol> typeConstraintTypeSymbol =
                BallerinaCompilerApi.getInstance().getType(types, document.get(), ballerinaType);
        if (typeConstraintTypeSymbol.isPresent()) {
            if (!typeSymbol.get().subtypeOf(typeConstraintTypeSymbol.get())) {
                String message = String.format(INVALID_SUBTYPE, ballerinaType, inputExpression);
                diagnostics.add(CommonUtils.createDiagnostic(message, context.getExpressionLineRange(),
                        "", DiagnosticSeverity.ERROR));
            }
        }
        return diagnostics;
    }

    /**
     * Imports the modules the type descriptor refers to into the probed file: the ones listed with the property, and
     * the ones another file of the module imports under a prefix the type uses but this file does not bind.
     *
     * @return the type descriptor, requalified onto the prefixes of the probed file
     */
    private static String addRequiredImports(ExpressionEditorContext context, String typeDescriptor) {
        Document document = context.documentContext().document();
        ModulePartNode rootNode = document.syntaxTree().rootNode();
        ModulePrefixContext prefixes = ModulePrefixContext.from(rootNode, ModuleInfo.from(document.module()
                .descriptor()));
        ExpressionEditorContext.Property property = context.getProperty();
        String requalified = property == null ? typeDescriptor
                : prefixes.requalifyAuthored(typeDescriptor, property.importStatements());

        List<TextEdit> textEdits = new ArrayList<>();
        for (String signature : prefixes.pendingImportStatements()) {
            context.getImport(signature).ifPresent(textEdits::add);
        }

        Set<String> boundPrefixes = new HashSet<>(ImportPrefixReader.importedPrefixes(rootNode));
        boundPrefixes.addAll(prefixes.pendingImports().values());
        Set<String> missingPrefixes = new LinkedHashSet<>(getModulePrefixes(requalified));
        missingPrefixes.removeAll(boundPrefixes);
        for (DocumentId documentId : document.module().documentIds()) {
            if (missingPrefixes.isEmpty()) {
                break;
            }
            if (documentId.equals(document.documentId())) {
                continue;
            }
            Node siblingRoot = document.module().document(documentId).syntaxTree().rootNode();
            if (!(siblingRoot instanceof ModulePartNode siblingModulePart)) {
                continue;
            }
            for (ImportDeclarationNode importNode : siblingModulePart.imports()) {
                if (missingPrefixes.remove(ImportPrefixReader.prefixOf(importNode))) {
                    textEdits.add(TextEdit.from(TextRange.from(0, 0),
                            importNode.toSourceCode().strip() + System.lineSeparator()));
                }
            }
        }

        if (!textEdits.isEmpty()) {
            context.applyTextEdits(textEdits);
        }
        return requalified;
    }

    private static Set<String> getModulePrefixes(String typeDescriptor) {
        Set<String> modulePrefixes = new HashSet<>();
        NodeParser.parseTypeDescriptor(typeDescriptor).accept(new NodeVisitor() {
            @Override
            public void visit(QualifiedNameReferenceNode qualifiedNameReferenceNode) {
                modulePrefixes.add(qualifiedNameReferenceNode.modulePrefix().text());
            }
        });
        return modulePrefixes;
    }

    private static boolean isVarType(String inputExpression) {
        return inputExpression.trim().equals(VAR_KEYWORD);
    }

    private String getTrimmedOutput(String text) {
        // TODO: The following is a temporary fix for the invalid diagnostic produced for the readonly flag in the
        //  type descriptor. Ideally, this should be a flag in the type editor (as it is not part of the type
        //  descriptor). Tracked with: https://github.com/wso2/product-ballerina-integrator/issues/150
        // If the input starts with "readonly ", then obtain the string after this prefix
        String trimmedInput = text.trim();
        if (trimmedInput.startsWith("readonly ")) {
            return trimmedInput.substring(9);
        }
        return text;
    }
}
