/*
 *  Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com)
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

import org.eclipse.lsp4j.Diagnostic;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Comparator;
import java.util.List;

/**
 * Tests for the detection of unescaped reserved keywords used as names in the expression editor diagnostics.
 *
 * @since 1.7.0
 */
public class ReservedKeywordDiagnosticsTest {

    @DataProvider(name = "keywordsAsNames")
    public Object[][] keywordsAsNames() {
        return new Object[][]{
                // Mapping constructor keys
                {"{start: 1}", List.of("start"), 1},
                {"{id: 1, function: \"x\"}", List.of("function"), 8},
                {"{start:1}", List.of("start"), 1},
                // Field access
                {"event.start", List.of("start"), 6},
                {"event?.type", List.of("type"), 7},
        };
    }

    @Test(dataProvider = "keywordsAsNames")
    public void testKeywordAsName(String expression, List<String> keywords, int firstCharacter) {
        List<Diagnostic> diagnostics = ReservedKeywordDiagnosticsTest.sorted(expression);
        Assert.assertEquals(diagnostics.size(), keywords.size(), expression);
        for (int i = 0; i < keywords.size(); i++) {
            Assert.assertEquals(diagnostics.get(i).getMessage(), String.format(
                    "`%1$s` is a reserved keyword; write it as `'%1$s` to use it as a name", keywords.get(i)));
        }
        Assert.assertEquals(diagnostics.getFirst().getRange().getStart().getLine(), 0);
        Assert.assertEquals(diagnostics.getFirst().getRange().getStart().getCharacter(), firstCharacter);
        Assert.assertEquals(diagnostics.getFirst().getRange().getEnd().getCharacter(),
                firstCharacter + keywords.getFirst().length());
    }

    @DataProvider(name = "noKeywordsAsNames")
    public Object[][] noKeywordsAsNames() {
        return new Object[][]{
                // Escaped keywords
                {"{'start: 1}"},
                {"event.'start"},
                // Names that are not keywords
                {"{id: 1, name: \"x\"}"},
                // Keywords a method call may use without escaping
                {"items.map(item => item * 2)"},
                // Module qualifiers that are keywords
                {"fn(a, string:length(s))"},
                {"[1, int:MAX_VALUE]"},
                // Keywords within literals
                {"\"{start: 1}\""},
                {"`event.type ${x}`"},
                // Keywords as values
                {"cond ? true : false"},
                {"{a: true, b: null}"},
        };
    }

    @Test(dataProvider = "noKeywordsAsNames")
    public void testNoKeywordAsName(String expression) {
        Assert.assertTrue(DiagnosticsRequest.getReservedKeywordDiagnostics(expression).isEmpty(), expression);
    }

    @Test
    public void testMultiLineRange() {
        List<Diagnostic> diagnostics = sorted("{\n    id: 1,\n    start: 2\n}");
        Assert.assertEquals(diagnostics.size(), 1);
        Assert.assertEquals(diagnostics.getFirst().getRange().getStart().getLine(), 2);
        Assert.assertEquals(diagnostics.getFirst().getRange().getStart().getCharacter(), 4);
    }

    private static List<Diagnostic> sorted(String expression) {
        return DiagnosticsRequest.getReservedKeywordDiagnostics(expression).stream()
                .sorted(Comparator.comparingInt((Diagnostic d) -> d.getRange().getStart().getLine())
                        .thenComparingInt(d -> d.getRange().getStart().getCharacter()))
                .toList();
    }
}
