/**
 * Copyright © 2016-2018 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.rule.engine.transform;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.thingsboard.rule.engine.TbNodeUtils;
import org.thingsboard.rule.engine.api.*;
import org.thingsboard.rule.engine.util.EntitiesCustomerIdAsyncLoader;
import org.thingsboard.rule.engine.util.EntitiesRelatedEntityIdAsyncLoader;
import org.thingsboard.rule.engine.util.EntitiesTenantIdAsyncLoader;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.HashSet;

@Slf4j
@RuleNode(
        type = ComponentType.TRANSFORMATION,
        name="change originator",
        configClazz = TbChangeOriginatorNodeConfiguration.class,
        nodeDescription = "Change Message Originator To Tenant/Customer/Related Entity",
        nodeDetails = "Related Entity found using configured relation direction and Relation Type. " +
                "If multiple Related Entities are found, only first Entity is used as new Originator, other entities are discarded. ")
public class TbChangeOriginatorNode extends TbAbstractTransformNode {

    protected static final String CUSTOMER_SOURCE = "CUSTOMER";
    protected static final String TENANT_SOURCE = "TENANT";
    protected static final String RELATED_SOURCE = "RELATED";

    private TbChangeOriginatorNodeConfiguration config;

    @Override
    public void init(TbNodeConfiguration configuration, TbNodeState state) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbChangeOriginatorNodeConfiguration.class);
        validateConfig(config);
        setConfig(config);
    }

    @Override
    protected ListenableFuture<TbMsg> transform(TbContext ctx, TbMsg msg) {
        ListenableFuture<? extends EntityId> newOriginator = getNewOriginator(ctx, msg.getOriginator());
        return Futures.transform(newOriginator, (Function<EntityId, TbMsg>) n -> new TbMsg(msg.getId(), msg.getType(), n, msg.getMetaData(), msg.getData()));
    }

    private ListenableFuture<? extends EntityId> getNewOriginator(TbContext ctx, EntityId original) {
        switch (config.getOriginatorSource()) {
            case CUSTOMER_SOURCE:
                return EntitiesCustomerIdAsyncLoader.findEntityIdAsync(ctx, original);
            case TENANT_SOURCE:
                return EntitiesTenantIdAsyncLoader.findEntityIdAsync(ctx, original);
            case RELATED_SOURCE:
                return EntitiesRelatedEntityIdAsyncLoader.findEntityAsync(ctx, original, config.getDirection(), config.getRelationType());
            default:
                return Futures.immediateFailedFuture(new IllegalStateException("Unexpected originator source " + config.getOriginatorSource()));
        }
    }

    private void validateConfig(TbChangeOriginatorNodeConfiguration conf) {
        HashSet<String> knownSources = Sets.newHashSet(CUSTOMER_SOURCE, TENANT_SOURCE, RELATED_SOURCE);
        if (!knownSources.contains(conf.getOriginatorSource())) {
            log.error("Unsupported source [{}] for TbChangeOriginatorNode", conf.getOriginatorSource());
            throw new IllegalArgumentException("Unsupported source TbChangeOriginatorNode" + conf.getOriginatorSource());
        }

        if (conf.getOriginatorSource().equals(RELATED_SOURCE)) {
            if (conf.getDirection() == null || StringUtils.isBlank(conf.getRelationType())) {
                log.error("Related source for TbChangeOriginatorNode should have direction and relationType. Actual [{}] [{}]",
                        conf.getDirection(), conf.getRelationType());
                throw new IllegalArgumentException("Wrong config for RElated Source in TbChangeOriginatorNode" + conf.getOriginatorSource());
            }
        }

    }

    @Override
    public void destroy() {

    }
}
