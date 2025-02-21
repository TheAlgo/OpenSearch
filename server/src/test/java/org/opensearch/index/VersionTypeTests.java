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
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.index;

import org.opensearch.common.lucene.uid.Versions;
import org.opensearch.test.OpenSearchTestCase;

import static org.hamcrest.Matchers.equalTo;

public class VersionTypeTests extends OpenSearchTestCase {
    public void testInternalVersionConflict() throws Exception {
        assertFalse(VersionType.INTERNAL.isVersionConflictForWrites(10, Versions.MATCH_ANY, randomBoolean()));
        assertFalse(VersionType.INTERNAL.isVersionConflictForReads(10, Versions.MATCH_ANY));

        // if we didn't find a version (but the index does support it), we don't like it unless MATCH_ANY
        assertTrue(VersionType.INTERNAL.isVersionConflictForWrites(Versions.NOT_FOUND, 10, randomBoolean()));
        assertTrue(VersionType.INTERNAL.isVersionConflictForReads(Versions.NOT_FOUND, 10));
        assertFalse(VersionType.INTERNAL.isVersionConflictForWrites(Versions.NOT_FOUND, Versions.MATCH_ANY, randomBoolean()));
        assertFalse(VersionType.INTERNAL.isVersionConflictForReads(Versions.NOT_FOUND, Versions.MATCH_ANY));

        assertEquals("current version [1] is different than the one provided [2]",
            VersionType.INTERNAL.explainConflictForReads(1, 2));
        assertEquals("document does not exist (expected version [2])",
            VersionType.INTERNAL.explainConflictForReads(Versions.NOT_FOUND, 2));

        // deletes
        assertFalse(VersionType.INTERNAL.isVersionConflictForWrites(Versions.NOT_FOUND, Versions.MATCH_DELETED, true));
        assertFalse(VersionType.INTERNAL.isVersionConflictForWrites(10, Versions.MATCH_DELETED, true));


        // and the stupid usual case
        assertFalse(VersionType.INTERNAL.isVersionConflictForWrites(10, 10, randomBoolean()));
        assertFalse(VersionType.INTERNAL.isVersionConflictForReads(10, 10));
        assertTrue(VersionType.INTERNAL.isVersionConflictForWrites(9, 10, randomBoolean()));
        assertTrue(VersionType.INTERNAL.isVersionConflictForReads(9, 10));
        assertTrue(VersionType.INTERNAL.isVersionConflictForWrites(10, 9, randomBoolean()));
        assertTrue(VersionType.INTERNAL.isVersionConflictForReads(10, 9));

// Old indexing code, dictating behavior
//        if (expectedVersion != Versions.MATCH_ANY && currentVersion != Versions.NOT_SET) {
//            // an explicit version is provided, see if there is a conflict
//            // if we did not find anything, and a version is provided, so we do expect to find a doc under that version
//            // this is important, since we don't allow to preset a version in order to handle deletes
//            if (currentVersion == Versions.NOT_FOUND) {
//                throw new VersionConflictEngineException(shardId, index.type(), index.id(), Versions.NOT_FOUND, expectedVersion);
//            } else if (expectedVersion != currentVersion) {
//                throw new VersionConflictEngineException(shardId, index.type(), index.id(), currentVersion, expectedVersion);
//            }
//        }
//        updatedVersion = (currentVersion == Versions.NOT_SET || currentVersion == Versions.NOT_FOUND) ? 1 : currentVersion + 1;
    }

    public void testVersionValidation() {
        assertTrue(VersionType.EXTERNAL.validateVersionForWrites(randomIntBetween(1, Integer.MAX_VALUE)));
        assertFalse(VersionType.EXTERNAL.validateVersionForWrites(Versions.MATCH_ANY));
        assertFalse(VersionType.EXTERNAL.validateVersionForWrites(randomIntBetween(Integer.MIN_VALUE, 0)));
        assertTrue(VersionType.EXTERNAL.validateVersionForReads(Versions.MATCH_ANY));
        assertTrue(VersionType.EXTERNAL.validateVersionForReads(randomIntBetween(1, Integer.MAX_VALUE)));
        assertFalse(VersionType.EXTERNAL.validateVersionForReads(randomIntBetween(Integer.MIN_VALUE, -1)));

        assertEquals("current version [1] is different than the one provided [2]",
            VersionType.EXTERNAL.explainConflictForReads(1, 2));
        assertEquals("document does not exist (expected version [2])",
            VersionType.EXTERNAL.explainConflictForReads(Versions.NOT_FOUND, 2));

        assertTrue(VersionType.EXTERNAL_GTE.validateVersionForWrites(randomIntBetween(1, Integer.MAX_VALUE)));
        assertFalse(VersionType.EXTERNAL_GTE.validateVersionForWrites(Versions.MATCH_ANY));
        assertFalse(VersionType.EXTERNAL_GTE.validateVersionForWrites(randomIntBetween(Integer.MIN_VALUE, 0)));
        assertTrue(VersionType.EXTERNAL_GTE.validateVersionForReads(Versions.MATCH_ANY));
        assertTrue(VersionType.EXTERNAL_GTE.validateVersionForReads(randomIntBetween(1, Integer.MAX_VALUE)));
        assertFalse(VersionType.EXTERNAL_GTE.validateVersionForReads(randomIntBetween(Integer.MIN_VALUE, -1)));

        assertEquals("current version [1] is different than the one provided [2]",
            VersionType.EXTERNAL_GTE.explainConflictForReads(1, 2));
        assertEquals("document does not exist (expected version [2])",
            VersionType.EXTERNAL_GTE.explainConflictForReads(Versions.NOT_FOUND, 2));

        assertTrue(VersionType.INTERNAL.validateVersionForWrites(randomIntBetween(1, Integer.MAX_VALUE)));
        assertTrue(VersionType.INTERNAL.validateVersionForWrites(Versions.MATCH_ANY));
        assertFalse(VersionType.INTERNAL.validateVersionForWrites(randomIntBetween(Integer.MIN_VALUE, 0)));
        assertTrue(VersionType.INTERNAL.validateVersionForReads(Versions.MATCH_ANY));
        assertTrue(VersionType.INTERNAL.validateVersionForReads(randomIntBetween(1, Integer.MAX_VALUE)));
        assertFalse(VersionType.INTERNAL.validateVersionForReads(randomIntBetween(Integer.MIN_VALUE, -1)));
    }

    public void testExternalVersionConflict() throws Exception {
        assertFalse(VersionType.EXTERNAL.isVersionConflictForWrites(Versions.NOT_FOUND, 10, randomBoolean()));
        // MATCH_ANY must throw an exception in the case of external version, as the version must be set! it used as the new value
        assertTrue(VersionType.EXTERNAL.isVersionConflictForWrites(10, Versions.MATCH_ANY, randomBoolean()));

        // if we didn't find a version (but the index does support it), we always accept
        assertFalse(VersionType.EXTERNAL.isVersionConflictForWrites(Versions.NOT_FOUND, Versions.NOT_FOUND, randomBoolean()));
        assertFalse(VersionType.EXTERNAL.isVersionConflictForWrites(Versions.NOT_FOUND, 10, randomBoolean()));

        assertTrue(VersionType.EXTERNAL.isVersionConflictForReads(Versions.NOT_FOUND, Versions.NOT_FOUND));
        assertTrue(VersionType.EXTERNAL.isVersionConflictForReads(Versions.NOT_FOUND, 10));
        assertFalse(VersionType.EXTERNAL.isVersionConflictForReads(Versions.NOT_FOUND, Versions.MATCH_ANY));

        // and the standard behavior
        assertTrue(VersionType.EXTERNAL.isVersionConflictForWrites(10, 10, randomBoolean()));
        assertFalse(VersionType.EXTERNAL.isVersionConflictForWrites(9, 10, randomBoolean()));
        assertTrue(VersionType.EXTERNAL.isVersionConflictForWrites(10, 9, randomBoolean()));

        assertFalse(VersionType.EXTERNAL.isVersionConflictForReads(10, 10));
        assertTrue(VersionType.EXTERNAL.isVersionConflictForReads(9, 10));
        assertTrue(VersionType.EXTERNAL.isVersionConflictForReads(10, 9));
        assertFalse(VersionType.EXTERNAL.isVersionConflictForReads(10, Versions.MATCH_ANY));


// Old indexing code, dictating behavior
//        // an external version is provided, just check, if a local version exists, that its higher than it
//        // the actual version checking is one in an external system, and we just want to not index older versions
//        if (currentVersion >= 0) { // we can check!, its there
//            if (currentVersion >= index.version()) {
//                throw new VersionConflictEngineException(shardId, index.type(), index.id(), currentVersion, index.version());
//            }
//        }
//        updatedVersion = index.version();
    }

    public void testExternalGTEVersionConflict() throws Exception {
        assertFalse(VersionType.EXTERNAL_GTE.isVersionConflictForWrites(Versions.NOT_FOUND, 10, randomBoolean()));
        // MATCH_ANY must throw an exception in the case of external version, as the version must be set! it used as the new value
        assertTrue(VersionType.EXTERNAL_GTE.isVersionConflictForWrites(10, Versions.MATCH_ANY, randomBoolean()));

        // if we didn't find a version (but the index does support it), we always accept
        assertFalse(VersionType.EXTERNAL_GTE.isVersionConflictForWrites(Versions.NOT_FOUND, Versions.NOT_FOUND, randomBoolean()));
        assertFalse(VersionType.EXTERNAL_GTE.isVersionConflictForWrites(Versions.NOT_FOUND, 10, randomBoolean()));

        assertTrue(VersionType.EXTERNAL_GTE.isVersionConflictForReads(Versions.NOT_FOUND, Versions.NOT_FOUND));
        assertTrue(VersionType.EXTERNAL_GTE.isVersionConflictForReads(Versions.NOT_FOUND, 10));
        assertFalse(VersionType.EXTERNAL_GTE.isVersionConflictForReads(Versions.NOT_FOUND, Versions.MATCH_ANY));


        // and the standard behavior
        assertFalse(VersionType.EXTERNAL_GTE.isVersionConflictForWrites(10, 10, randomBoolean()));
        assertFalse(VersionType.EXTERNAL_GTE.isVersionConflictForWrites(9, 10, randomBoolean()));
        assertTrue(VersionType.EXTERNAL_GTE.isVersionConflictForWrites(10, 9, randomBoolean()));

        assertFalse(VersionType.EXTERNAL_GTE.isVersionConflictForReads(10, 10));
        assertTrue(VersionType.EXTERNAL_GTE.isVersionConflictForReads(9, 10));
        assertTrue(VersionType.EXTERNAL_GTE.isVersionConflictForReads(10, 9));
        assertFalse(VersionType.EXTERNAL_GTE.isVersionConflictForReads(10, Versions.MATCH_ANY));

    }

    public void testUpdateVersion() {
        assertThat(VersionType.INTERNAL.updateVersion(Versions.NOT_FOUND, 10), equalTo(1L));
        assertThat(VersionType.INTERNAL.updateVersion(1, 1), equalTo(2L));
        assertThat(VersionType.INTERNAL.updateVersion(2, Versions.MATCH_ANY), equalTo(3L));


        assertThat(VersionType.EXTERNAL.updateVersion(Versions.NOT_FOUND, 10), equalTo(10L));
        assertThat(VersionType.EXTERNAL.updateVersion(1, 10), equalTo(10L));

        assertThat(VersionType.EXTERNAL_GTE.updateVersion(Versions.NOT_FOUND, 10), equalTo(10L));
        assertThat(VersionType.EXTERNAL_GTE.updateVersion(1, 10), equalTo(10L));
        assertThat(VersionType.EXTERNAL_GTE.updateVersion(10, 10), equalTo(10L));

// Old indexing code
//        if (index.versionType() == VersionType.INTERNAL) { // internal version type
//            updatedVersion = (currentVersion == Versions.NOT_SET || currentVersion == Versions.NOT_FOUND) ? 1 : currentVersion + 1;
//        } else { // external version type
//            updatedVersion = expectedVersion;
//        }
    }
}
