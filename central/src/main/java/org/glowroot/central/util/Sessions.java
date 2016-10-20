/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.central.util;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidConfigurationInQueryException;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class Sessions {

    private static final Logger logger = LoggerFactory.getLogger(Sessions.class);

    private static final String WITH_TWCS =
            " with compaction = { 'class' : 'TimeWindowCompactionStrategy' }";
    private static final String WITH_DTCS =
            " with compaction = { 'class' : 'DateTieredCompactionStrategy' }";

    private Sessions() {}

    public static void createKeyspaceIfNotExists(Session session, String keyspace) {
        session.execute("create keyspace if not exists " + keyspace + " with replication"
                + " = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }");
    }

    public static void createTableWithTWCS(Session session, String createTableQuery) {
        try {
            session.execute(createTableQuery + WITH_TWCS);
        } catch (InvalidConfigurationInQueryException e) {
            logger.debug(e.getMessage(), e);
            session.execute(createTableQuery + WITH_DTCS);
        }
    }

    public static ResultSetFuture executeAsyncWithOnFailure(Session session,
            BoundStatement boundStatement, Runnable onFailure) {
        ResultSetFuture future = session.executeAsync(boundStatement);
        future.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    // TODO report checker framework issue that occurs without checkNotNull
                    checkNotNull(future).getUninterruptibly();
                } catch (Exception e) {
                    logger.debug(e.getMessage(), e);
                    onFailure.run();
                }
            }
        }, MoreExecutors.directExecutor());
        return future;
    }
}
