/*
 * Copyright 2012-2017 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.data.aerospike.repository.query;

import org.springframework.data.aerospike.query.Qualifier;

import java.util.Objects;

/**
 * @author Michael Zhang
 * @author Jeff Boone
 */
public class AerospikeCriteria extends Qualifier implements CriteriaDefinition {

    public AerospikeCriteria(Qualifier.Builder builder) {
        super(builder);
    }

    @Override
    public Qualifier getCriteriaObject() {
        return this;
    }

    @Override
    public String getKey() {
        return this.getField();
    }

    protected static boolean isSingleIdQuery(AerospikeCriteria criteria) {
        return Objects.equals(criteria.getField(), Qualifier.ID_VALUE);
    }
}
