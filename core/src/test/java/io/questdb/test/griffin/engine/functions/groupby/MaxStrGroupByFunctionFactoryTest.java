/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2024 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.test.griffin.engine.functions.groupby;

import io.questdb.PropertyKey;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.cairo.sql.RecordCursorFactory;
import io.questdb.griffin.SqlException;
import io.questdb.test.AbstractCairoTest;
import io.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Test;

public class MaxStrGroupByFunctionFactoryTest extends AbstractCairoTest {

    @Test
    public void testConstant() throws Exception {
        assertQuery(
                "a\tmax\n" +
                        "a\t42\n" +
                        "b\t42\n" +
                        "c\t42\n",
                "select a, max('42') from x order by a",
                "create table x as (select * from (select rnd_symbol('a','b','c') a from long_sequence(20)))",
                null,
                true,
                true
        );
    }

    @Test
    public void testExpression() throws Exception {
        assertQuery(
                "a\tmax\n" +
                        "a\tcccccc\n" +
                        "b\tcccccc\n" +
                        "c\tcccccc\n",
                "select a, max(concat(s, s)) from x order by a",
                "create table x as (select * from (select rnd_symbol('a','b','c') a, rnd_str('aaa','bbb','ccc') s from long_sequence(20)))",
                null,
                true,
                true
        );
    }

    @Test
    public void testGroupKeyed() throws Exception {
        assertQuery(
                "a\tmax\n" +
                        "a\t333\n" +
                        "b\t333\n" +
                        "c\t333\n",
                "select a, max(s) from x order by a",
                "create table x as (select * from (select rnd_symbol('a','b','c') a, rnd_str('111','222','333') s, timestamp_sequence(0, 100000) ts from long_sequence(20)) timestamp(ts))",
                null,
                true,
                true
        );
    }

    @Test
    public void testGroupNotKeyed() throws Exception {
        assertQuery(
                "max\n" +
                        "a2\n",
                "select max(s) from x",
                "create table x as (select * from (select rnd_str('a','a1','a2') s, timestamp_sequence(0, 100000) ts from long_sequence(100)) timestamp(ts))",
                null,
                false,
                true
        );
    }

    @Test
    public void testGroupNotKeyedWithNulls() throws Exception {
        assertMemoryLeak(() -> {
            String expected = "max\n" +
                    "c\n";
            assertQueryNoLeakCheck(
                    expected,
                    "select max(s) from x",
                    "create table x as (select * from (select rnd_str('a','b','c') s, timestamp_sequence(10, 100000) ts from long_sequence(100)) timestamp(ts)) timestamp(ts) PARTITION BY YEAR",
                    null,
                    false,
                    true
            );

            execute("insert into x values(cast(null as STRING), '2021-05-21')");
            execute("insert into x values(cast(null as STRING), '1970-01-01')");
            assertSql(expected, "select max(s) from x");
        });
    }

    @Test
    public void testLargeStrings() throws Exception {
        node1.setProperty(PropertyKey.CAIRO_SQL_GROUPBY_ALLOCATOR_DEFAULT_CHUNK_SIZE, 128);
        assertQuery(
                "a\tlength\n" +
                        "a\t7439\n" +
                        "b\t2740\n" +
                        "c\t3504\n",
                "select a, length(s) from (select a, max(s) s from x) order by a",
                "create table x as (select rnd_symbol('a','b','c') a, rnd_str(10,10000,2) s from long_sequence(1000))",
                null,
                true,
                true
        );
    }

    @Test
    public void testNullConstant() throws Exception {
        assertQuery(
                "a\tmax\n" +
                        "a\t\n" +
                        "b\t\n" +
                        "c\t\n",
                "select a, max(cast(null as STRING)) from x order by a",
                "create table x as (select * from (select rnd_symbol('a','b','c') a from long_sequence(20)))",
                null,
                true,
                true
        );
    }

    @Test
    public void testSampleFillLinearNotSupported() throws Exception {
        assertMemoryLeak(() -> {
            execute("create table x as (select * from (select rnd_int() i, rnd_str('a','b','c') s, timestamp_sequence(0, 100000) ts from long_sequence(100)) timestamp(ts))");
            try (
                    final RecordCursorFactory factory = select("select ts, avg(i), max(s) from x sample by 1s fill(linear)");
                    final RecordCursor cursor = factory.getCursor(sqlExecutionContext)
            ) {
                cursor.hasNext();
                Assert.fail();
            } catch (SqlException e) {
                TestUtils.assertContains(e.getMessage(), "support for LINEAR fill is not yet implemented");
            }
        });
    }

    @Test
    public void testSampleKeyed() throws Exception {
        assertQuery(
                "a\tmax\tts\n" +
                        "a\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "b\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "f\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "c\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "e\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "d\tедно\t1970-01-01T00:00:00.000000Z\n" +
                        "d\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "b\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "a\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "c\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "f\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "e\tедно\t1970-01-01T00:00:05.000000Z\n",
                "select a, max(s), ts from x sample by 5s align to first observation",
                "create table x as (select * from (select rnd_symbol('a','b','c','d','e','f') a, rnd_str('едно','две','три') s, timestamp_sequence(0, 100000) ts from long_sequence(100)) timestamp(ts))",
                "ts",
                false
        );
        assertQuery(
                "a\tmax\tts\n" +
                        "a\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "b\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "c\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "d\tедно\t1970-01-01T00:00:00.000000Z\n" +
                        "e\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "f\tтри\t1970-01-01T00:00:00.000000Z\n" +
                        "a\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "b\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "c\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "d\tтри\t1970-01-01T00:00:05.000000Z\n" +
                        "e\tедно\t1970-01-01T00:00:05.000000Z\n" +
                        "f\tтри\t1970-01-01T00:00:05.000000Z\n",
                "select a, max(s), ts from x sample by 5s align to calendar order by 3, 1",
                "ts",
                true,
                true
        );
    }
}
