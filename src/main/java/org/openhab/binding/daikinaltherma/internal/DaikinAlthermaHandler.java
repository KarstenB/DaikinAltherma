/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.daikinaltherma.internal;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelGroupUID;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelGroupType;
import org.eclipse.smarthome.core.thing.type.ChannelGroupTypeBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelGroupTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DaikinAlthermaHandler} is responsible for handling commands, which
 * are sent to one of the channels.
 *
 * @author Karsten Becker - Initial contribution
 */
public class DaikinAlthermaHandler extends BaseThingHandler {

    private static final String ITEM_SEP = "/";

    private final Logger logger = LoggerFactory.getLogger(DaikinAlthermaHandler.class);

    private @Nullable DaikinAlthermaConfiguration config;

    private Map<ChannelUID, String> channelToItem = new HashMap<>();

    private ScheduledFuture<?> task;

    private WebsocketHelper webSocketClient;

    public DaikinAlthermaHandler(Thing thing, WebsocketHelper webSocketClient) {
        super(thing);
        this.webSocketClient = webSocketClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            String item = channelToItem.get(channelUID);
            logger.debug("Found "+item+" for "+channelUID+" to update");
            if (item != null) {
                updateChannel(item, channelUID);
            }
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(DaikinAlthermaConfiguration.class);

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            if (config != null) {
                refineThingFromURL(config.host, config.port);
                task = scheduler.scheduleWithFixedDelay(() -> updateChannels(config.host, config.port), 5,
                        config.interval, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public void dispose() {
        if (task != null)
            task.cancel(true);
    }

    private void updateChannels(String host, int port) {
        URI url = URI.create("ws://" + host + ":" + port + "/mca");
        logger.debug("Updating channels");
        try {
            if (!webSocketClient.connect(url))
                return;
            for (Channel ch : getThing().getChannels()) {
                if (!isLinked(ch.getUID()))
                    break;
                String item = channelToItem.get(ch.getUID());
                updateChannel(item, ch.getUID());
            }
        } finally {
            webSocketClient.disconnect();
        }
        logger.debug("Updating done");
    }

    private void updateChannel(String item, ChannelUID uid) {
        Optional<JsonObject> res = webSocketClient.doQuery(item+"/la");
        if (!res.isPresent())
            return;
        Optional<JsonElement> sub = getJsonPath(res.get(), "pc", "m2m:cin", "con");
        if (!sub.isPresent())
            return;
        String valObj = sub.get().getAsString();

        postCommand(uid, new StringType(valObj));
    }

    private void refineThingFromURL(String host, int port) {
        URI url = URI.create("ws://" + host + ":" + port + "/mca");
        try {
            if (!webSocketClient.connect(url))
                return;
            int code = -1;
            int i = 0;
            List<Channel> channels = new LinkedList<>();
            do {
                Optional<JsonObject> obj = webSocketClient.doQuery(Integer.toString(i));
                if (!obj.isPresent())
                    return;
                code = obj.get().get("rsc").getAsInt();
                if (code == 2000) {
                    String groupName=Integer.toString(i);
                    Optional<JsonElement> label = getJsonPath(obj.get(), "pc", "m2m:cnt", "lbl");
                    if (label.isPresent() && label.get().isJsonPrimitive()){
                        String temp=label.get().getAsString();
                        groupName=temp.substring(temp.indexOf("/")+1);
                    }
                    Optional<JsonObject> res = webSocketClient.doQuery(i + "/UnitProfile/la");
                    if (!res.isPresent())
                        return;
                    Optional<JsonElement> sub = getJsonPath(res.get(), "pc", "m2m:cin", "con");
                    if (!sub.isPresent())
                        return;
                    String valObj = sub.get().getAsString();
                    logger.debug("Profile:" + valObj);
                    ChannelGroupType groupType = ChannelGroupTypeBuilder
                            .instance(DaikinAlthermaBindingConstants.CHANNEL_GROUP, groupName)
                            .isAdvanced(false)
                            .withDescription("All channels of the item:"+groupName)
                            .build();
                        
                    new ChannelGroupUID()
                    createChannelsFromJSON(valObj, i, channels, group);
                }
                // {"m2m:rsp":{"rsc":2000,"rqi":"12e741f64af0afd2","to":"/OpenHab","fr":"/[0]/MNAE/0/la","pc":{"m2m:cin":{"rn":"0000000b","ri":"006a_0000000b","pi":"006a","ty":4,"ct":"20000000T000000Z","lt":"20000000T000000Z","st":11,"con":"{\"version\":\"v1.2.3\"}"}}}}
                // {"m2m:rsp":{"rsc":4004,"rqi":"1c3ac0b7592824ee","to":"/OpenHab","fr":"/[0]/MNAE/3/la"}}
                i++;
            } while (code == 2000);
            ThingBuilder builder = editThing();
            builder.withChannels(channels);
            updateThing(builder.build());
        } finally {
            webSocketClient.disconnect();
        }
        updateStatus(ThingStatus.ONLINE);
    }

    private Optional<JsonElement> getJsonPath(JsonObject obj, String... keys) {
        return getJsonPath(obj, false, keys);
    }

    public Optional<JsonElement> getJsonPath(JsonObject obj, boolean allowNonExist, String... keys) {
        if (keys == null || keys.length == 0)
            return Optional.of(obj);
        for (int i = 0; i < keys.length; i++) {
            String var = keys[i];
            JsonElement sub = obj.get(var);
            if (sub == null) {
                if (!allowNonExist)
                    logger.warn("Expected to find member:" + var + " in " + obj);
                return Optional.empty();
            }
            if (sub.isJsonObject())
                obj = sub.getAsJsonObject();
            else if (i != keys.length - 1) {
                if (!allowNonExist)
                    logger.warn("Expected to find member:" + var + " in " + obj);
                return Optional.empty();
            }
        }
        return Optional.of(obj.get(keys[keys.length - 1]));
    }

    private void createChannelsFromJSON(String valObj, int i, List<Channel> channels, ChannelGroupUID group) {
        JsonObject obj = new JsonParser().parse(valObj).getAsJsonObject();
        Set<String> primitives = new HashSet<>();
        buildChannels(obj, Integer.toString(i), channels, primitives, group);
    }

    private void buildChannels(JsonElement root, String item, List<Channel> channels, Set<String> primitives, ChannelGroupUID group) {
        if (root.isJsonPrimitive()) {
            item += ITEM_SEP + root.getAsString();
            primitives.add(item);
        }
        try {
            Optional<JsonObject> obj = webSocketClient.doQuery(item+"/la");
            if (!obj.isPresent()) // There should be enough debug output in doQuery
                return;
            int code = obj.get().get("rsc").getAsInt();
            logger.trace("Obj:" + item + " " + code + " " + obj);
            if (code == 2000) {
                logger.debug("Found channel:" + item);
                if (item.length()>1) { // This drops channels that are at the root because they don't provide any
                                   // interesting data
                    String key = item.replaceAll(ITEM_SEP, "_");
                    ChannelUID uid = new ChannelUID(group, key);
                    Channel chan = ChannelBuilder.create(uid, null)//
                            .withLabel(item.replaceAll(ITEM_SEP, " -> "))//
                            .build();
                    channels.add(chan);
                    channelToItem.put(uid, item);
                }
            }
        } catch (Exception e) {
            logger.debug("Error:", e);
            return;
        }
        if (root.isJsonArray()) {
            JsonArray array = root.getAsJsonArray();
            for (JsonElement ele : array) {
                buildChannels(ele, item, channels, primitives, group);
            }
            return;
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (String key : obj.keySet()) {
                JsonElement ele = obj.get(key);
                buildChannels(ele, item + ITEM_SEP + key, channels, primitives, group);
            }
            return;
        }
    }

}
