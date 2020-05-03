package org.openhab.binding.daikinaltherma.internal;

import static org.openhab.binding.daikinaltherma.internal.DaikinAlthermaBindingConstants.BINDING_ID;
import static org.openhab.binding.daikinaltherma.internal.DaikinAlthermaBindingConstants.PARAM_HOST;
import static org.openhab.binding.daikinaltherma.internal.DaikinAlthermaBindingConstants.PARAM_PORT;
import static org.openhab.binding.daikinaltherma.internal.DaikinAlthermaBindingConstants.PARAM_SW_VERSION;
import static org.openhab.binding.daikinaltherma.internal.DaikinAlthermaBindingConstants.PARAM_TYPE;
import static org.openhab.binding.daikinaltherma.internal.DaikinAlthermaBindingConstants.THING_TYPE_ADAPTER;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.io.net.http.WebSocketFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = MDNSDiscoveryParticipant.class, immediate = true, configurationPid = "discovery.daikinaltherma")
public class DaikinAlthermaMDNSDiscoveryParticipant implements MDNSDiscoveryParticipant {

    /**
     * the shared web socket client
     */
    private @NonNullByDefault({}) WebSocketClient webSocketClient;
    private WebsocketHelper helper;

    @Activate
    public DaikinAlthermaMDNSDiscoveryParticipant(@Reference WebSocketFactory webSocketFactory) {
        this.webSocketClient = webSocketFactory.getCommonWebSocketClient();
        this.helper = new WebsocketHelper(webSocketClient);
    }

    public DaikinAlthermaMDNSDiscoveryParticipant(WebsocketHelper helper) {
        this.helper=helper;
    }


    private final Logger logger = LoggerFactory.getLogger(DaikinAlthermaMDNSDiscoveryParticipant.class);

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(DaikinAlthermaBindingConstants.THING_TYPE_ADAPTER);
    }

    @Override
    public String getServiceType() {
        // return "_http._tcp.local.";
        return "_daikin._tcp.local.";
    }

    @Override
    public @Nullable DiscoveryResult createResult(ServiceInfo service) {
        InetAddress[] adresses = service.getInetAddresses();
        logger.info(service.getName() + " " + Arrays.toString(adresses) + " " + service.getQualifiedName());

        if (adresses != null && adresses.length > 0) {
            InetAddress adr = adresses[0];
            int port = 80;
            if (service.getPort() > 0)
                port = service.getPort();
            String url = "ws://" + adr.getHostAddress() + ":" + port + "/mca";
            logger.debug("URL:" + url);
            try {
                if (!helper.connect(URI.create(url))){
                    logger.debug("Discovery failed to connect to:"+url);
                    return null;
                }
                Optional<String> reply = helper.sendDiscovery();
                if (!reply.isPresent())
                    return null;
                JsonParser parser = new JsonParser();
                JsonElement root = parser.parse(reply.get());
                JsonObject description = root.getAsJsonObject().get("m2m:rsp").getAsJsonObject().get("pc")
                        .getAsJsonObject().get("m2m:dvi").getAsJsonObject();
                String id = description.get("dlb").getAsString();
                String model = description.get("mod").getAsString();
                String type = description.get("dty").getAsString();
                String firmware = description.get("fwv").getAsString();
                String software = description.get("swv").getAsString();
                String hardware = description.get("hwv").getAsString();
                Map<String, Object> properties = new HashMap<>();
                properties.put(PARAM_HOST, adr.getHostAddress());
                properties.put(PARAM_PORT, port);
                properties.put(Thing.PROPERTY_SERIAL_NUMBER, id);
                properties.put(Thing.PROPERTY_MODEL_ID, model);
                properties.put(PARAM_TYPE, type);
                properties.put(Thing.PROPERTY_FIRMWARE_VERSION, firmware);
                properties.put(Thing.PROPERTY_HARDWARE_VERSION, hardware);
                properties.put(PARAM_SW_VERSION, software);
                logger.debug("Found " + id + " model:" + model + " " + type + " " + firmware + " " + software);
                ThingUID thingID = new ThingUID(THING_TYPE_ADAPTER, id);
                return DiscoveryResultBuilder.create(thingID)//
                        .withLabel("Daikin " + model + " " + id)//
                        .withProperties(properties)//
                        .withThingType(THING_TYPE_ADAPTER)//
                        .withRepresentationProperty(id)//
                        .build();
            } catch (IOException | InterruptedException e) {
                logger.debug("Connection exception:", e);
            } finally {
                helper.disconnect();
            }
        }
        return null;
    }

    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        System.out.println("Service queried for getThingID:"+service+" "+service.getName());
        logger.debug("Service queried for getThingID:"+service+" "+service.getName());
        return new ThingUID(THING_TYPE_ADAPTER, service.getName());
    }
}