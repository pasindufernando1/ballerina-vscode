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

import com.google.gson.JsonObject;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.SyntaxInfo;
import io.ballerina.flowmodelgenerator.core.expressioneditor.ExpressionEditorContext;
import io.ballerina.flowmodelgenerator.core.model.Property;
import io.ballerina.modelgenerator.commons.CommonUtils;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.text.LinePosition;
import io.ballerina.tools.text.LineRange;
import org.eclipse.lsp4j.Diagnostic;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Represents a request to retrieve diagnostics for a given file and context. This class extends
 * DebouncedExpressionEditorRequest to handle diagnostic information for Ballerina expressions with debouncing
 * capability. This is an abstract class that sets the common functionality to handle syntax and semantic errors for an
 * expression editor. It provides the basic processing workflow to parse the input expression, extract syntax errors,
 * and then, if no syntax issues are found, retrieve semantic diagnostics.
 *
 * @since 1.0.0
 */
public abstract class DiagnosticsRequest extends DebouncedExpressionEditorRequest<DiagnosticsRequest.Diagnostics> {

    private static final String RESERVED_KEYWORD_MESSAGE =
            "`%1$s` is a reserved keyword; write it as `'%1$s` to use it as a name";
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|`[^`]*`");
    // A name after '{' or ',' followed by ':' (a mapping key), or after '.' / '?.' not followed by '(' (a field). A
    // colon directly followed by a name is a module qualifier instead, e.g. string:length
    private static final Pattern KEYWORD_AS_NAME_PATTERN = Pattern.compile(
            "[{,]\\s*([a-z]+)\\s*:(?![:A-Za-z_'])|\\??\\.\\s*([a-z]+)\\b(?!\\s*\\()");

    private final ExpressionEditorContext context;

    public DiagnosticsRequest(ExpressionEditorContext context) {
        super(context);
        this.context = context;
    }

    public static DiagnosticsRequest from(ExpressionEditorContext context) {
        ExpressionEditorContext.Property property = context.getProperty();
        if (property == null) {
            throw new IllegalArgumentException("Property cannot be null");
        }

        Property.ValueType fieldType = property.propertyType().fieldType();
        return switch (fieldType) {
            case EXPRESSION, SQL_QUERY, RECORD_MAP_EXPRESSION, PROMPT -> new ExpressionDiagnosticsRequest(context);
            case LV_EXPRESSION -> new LvExpressionDiagnosticRequest(context);
            case ACTION_OR_EXPRESSION -> new ActionOrExpressionDiagnosticsRequest(context);
            case IDENTIFIER -> new IdentifierDiagnosticsRequest(context);
            case TYPE, ACTION_TYPE -> new TypeDiagnosticRequest(context);
            case CLAUSE_EXPRESSION -> new DataMapperClauseDiagnosticsRequest(context);
            default -> throw new IllegalArgumentException("Unsupported property type: " + fieldType);
        };
    }

    /**
     * Parses the provided text and returns the corresponding ST node.
     *
     * @param text the text to be parsed
     * @return the parsed ST node
     */
    protected abstract Node getParsedNode(String text);

    /**
     * Retrieves the set of semantic diagnostics for the given text.
     *
     * @param context the expression editor context to analyze for semantic issues
     * @return a set of diagnostics representing semantic errors
     */
    protected abstract Set<Diagnostic> getSemanticDiagnostics(ExpressionEditorContext context);

    protected Set<Diagnostic> getSyntaxDiagnostics(ExpressionEditorContext context) {
        Node parsedNode = getParsedNode(context.info().expression());
        return StreamSupport.stream(parsedNode.diagnostics().spliterator(), true)
                .map(CommonUtils::transformBallerinaDiagnostic)
                .collect(Collectors.toSet());
    }

    @Override
    public Diagnostics getResponse(ExpressionEditorContext context) {
        // Check for syntax errors
        Set<Diagnostic> syntaxDiagnostics = getSyntaxDiagnostics(context);
        if (!syntaxDiagnostics.isEmpty()) {
            // An unescaped reserved keyword used as a name surfaces as an obscure parser error, so it is reported as
            // the cause instead
            Set<Diagnostic> keywordDiagnostics = getReservedKeywordDiagnostics(context.info().expression());
            return new Diagnostics(keywordDiagnostics.isEmpty() ? syntaxDiagnostics : keywordDiagnostics);
        }

        // Check for semantic errors
        return new Diagnostics(getSemanticDiagnostics(context));
    }

    /**
     * Finds reserved keywords used without the escaping quote as a mapping constructor key (e.g. {@code {start: 1}})
     * or as an accessed field (e.g. {@code event.start}). Method calls are left out, as the parser accepts a few
     * keywords there (e.g. {@code arr.map(...)}).
     *
     * @param expression the expression to scan
     * @return a diagnostic per reserved keyword found, with ranges relative to the expression
     */
    static Set<Diagnostic> getReservedKeywordDiagnostics(String expression) {
        Set<Diagnostic> diagnostics = new HashSet<>();
        if (expression == null || expression.isEmpty()) {
            return diagnostics;
        }
        // Blank out string literals and templates so their contents are not mistaken for names, keeping offsets
        Matcher literalMatcher = STRING_LITERAL_PATTERN.matcher(expression);
        StringBuilder masked = new StringBuilder(expression);
        while (literalMatcher.find()) {
            for (int i = literalMatcher.start(); i < literalMatcher.end(); i++) {
                if (masked.charAt(i) != '\n') {
                    masked.setCharAt(i, ' ');
                }
            }
        }

        Matcher nameMatcher = KEYWORD_AS_NAME_PATTERN.matcher(masked);
        while (nameMatcher.find()) {
            String keyword = nameMatcher.group(1) != null ? nameMatcher.group(1) : nameMatcher.group(2);
            int start = nameMatcher.group(1) != null ? nameMatcher.start(1) : nameMatcher.start(2);
            if (!SyntaxInfo.isKeyword(keyword)) {
                continue;
            }
            LinePosition startPosition = toLinePosition(expression, start);
            LinePosition endPosition = toLinePosition(expression, start + keyword.length());
            diagnostics.add(CommonUtils.createDiagnostic(String.format(RESERVED_KEYWORD_MESSAGE, keyword),
                    LineRange.from("", startPosition, endPosition), "", DiagnosticSeverity.ERROR));
        }
        return diagnostics;
    }

    private static LinePosition toLinePosition(String text, int offset) {
        int line = 0;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return LinePosition.from(line, offset - lineStart);
    }

    @Override
    public String getKey() {
        // Debounced per field, so that validating one field does not cancel the pending validation of another
        ExpressionEditorContext.Info info = context.info();
        return "diagnostics:" + info.startLine() + ":" + getPropertyLabel(info);
    }

    private static String getPropertyLabel(ExpressionEditorContext.Info info) {
        JsonObject property = info.property();
        if (property == null || !property.has("metadata") || !property.get("metadata").isJsonObject()) {
            return "";
        }
        JsonObject metadata = property.getAsJsonObject("metadata");
        return metadata.has("label") && metadata.get("label").isJsonPrimitive()
                ? metadata.get("label").getAsString() : "";
    }

    @Override
    public long getDelay() {
        return 350;
    }

    public record Diagnostics(Set<Diagnostic> diagnostics) {
    }
}
