/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.Index;
import org.opensearch.index.analysis.pl.PolishStemTokenFilterFactory;
import org.opensearch.plugin.analysis.stempel.AnalysisStempelPlugin;
import org.opensearch.test.OpenSearchTestCase;
import org.hamcrest.MatcherAssert;

import java.io.IOException;

import static org.hamcrest.Matchers.instanceOf;

public class PolishAnalysisTests extends OpenSearchTestCase {
    public void testDefaultsPolishAnalysis() throws IOException {
        final TestAnalysis analysis = createTestAnalysis(new Index("test", "_na_"), Settings.EMPTY,
                new AnalysisStempelPlugin());
        TokenFilterFactory tokenizerFactory = analysis.tokenFilter.get("polish_stem");
        MatcherAssert.assertThat(tokenizerFactory, instanceOf(PolishStemTokenFilterFactory.class));

        Analyzer analyzer = analysis.indexAnalyzers.get("polish").analyzer();
        MatcherAssert.assertThat(analyzer, instanceOf(PolishAnalyzer.class));
    }
}
