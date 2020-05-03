package org.openhab.binding.daikinaltherma.internal.tests;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.jmdns.ServiceInfo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerCallback;
import org.eclipse.smarthome.core.thing.link.ItemChannelLink;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.binding.daikinaltherma.internal.DaikinAlthermaBindingConstants;
import org.openhab.binding.daikinaltherma.internal.DaikinAlthermaHandler;
import org.openhab.binding.daikinaltherma.internal.DaikinAlthermaMDNSDiscoveryParticipant;
import org.openhab.binding.daikinaltherma.internal.WebsocketHelper;

public class DaikinAlthermaHandlerTest {

    private DaikinAlthermaHandler handler;

    @Mock
    private ThingHandlerCallback callback;

    @Mock
    private Thing thing;

    private WebsocketHelper websocket = new WebsocketHelper(createWebsocketClient()) {

        private Map<String, String> responses = new HashMap<>();

        {
            try {
                //InputStream is = new FileInputStream("/Users/karstenbecker/PlatformIO/Projects/openhab2-addons/bundles/org.openhab.binding.daikinaltherma/src/test/java/responseCapture.txt");
                InputStream is = getClass().getResourceAsStream("/responseCapture.txt");
                assertNotNull("Failed to load responseCapture",is);
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line = null;
                while ((line = br.readLine()) != null) {
                    int idx = line.indexOf(' ');
                    String item = line.substring(0, idx);
                    String content = line.substring(idx + 1);
                    responses.put(item, content);
                }
                br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public boolean connect(URI url) {
            return true;
            //return super.connect(url);
        }

        @Override
        public Optional<String> sendQuery(String item) {
            String response=responses.get(item);
            return Optional.ofNullable(response);
        }

        public Optional<String> sendDiscovery() {
            return Optional.of("{\"m2m:rsp\":{\"rsc\":2000,\"rqi\":\"32751f8541ccfa6b\",\"to\":\"/OpenHab\",\"fr\":\"/[0]/MNCSE-node/deviceInfo\",\"pc\":{\"m2m:dvi\":{\"rn\":\"deviceInfo\",\"ri\":\"0077\",\"pi\":\"0075\",\"ty\":13,\"ct\":\"20000000T000000Z\",\"lt\":\"20000000T000000Z\",\"st\":0,\"mgd\":1007,\"dlb\":\"175000133\",\"man\":\"Daikin\",\"mod\":\"BRP069A62\",\"dty\":\"HVAC controller\",\"fwv\":\"17003905\",\"swv\":\"436CC099000\",\"hwv\":\"\"}}}}");
        }

    };

    @Before
    public void setUp() throws IOException, InterruptedException {
        MockitoAnnotations.initMocks(this);
        when(thing.getConfiguration()).thenReturn(new Configuration());
        when(thing.getUID()).thenReturn(new ThingUID(DaikinAlthermaBindingConstants.THING_TYPE_ADAPTER, "1234"));
        handler = new DaikinAlthermaHandler(thing, websocket);
        handler.setCallback(callback);
    }

    private WebSocketClient createWebsocketClient() {
        WebSocketClient client = new WebSocketClient();
        try {
            client.start();
        } catch (Exception e) {
            e.printStackTrace();
            return mock(WebSocketClient.class);
        }
        return client;
        //return mock(WebSocketClient.class);
    }

    @After
    public void tearDown() {
        // Free any resources, like open database connections, files etc.
        handler.dispose();
    }

    @Test
    public void initializeThing() {
        // we expect the handler#initialize method to call the callback during execution
        // and
        // pass it the thing and a ThingStatusInfo object containing the ThingStatus of
        // the thing.
        handler.initialize();

        // verify the interaction with the callback.
        // Check that the ThingStatusInfo given as second parameter to the callback was
        // build with the ONLINE status:
        InOrder inOrder = inOrder(callback);
        inOrder.verify(callback).statusUpdated(any(), argThat(arg -> arg.getStatus().equals(ThingStatus.UNKNOWN)));
        inOrder.verify(callback, timeout(100000)).statusUpdated(any(), argThat(arg -> arg.getStatus().equals(ThingStatus.ONLINE)));
        Thing newThing=handler.getThing();
        assertEquals("Expected a different number of channels", 24, newThing.getChannels().size());
        verify(callback).thingUpdated(any());
    }

    @Test
    public void testDiscovery() throws UnknownHostException {
        DaikinAlthermaMDNSDiscoveryParticipant discover = new DaikinAlthermaMDNSDiscoveryParticipant(websocket);
        ServiceInfo service = mock(ServiceInfo.class);
        InetAddress IP = InetAddress.getByName("192.168.188.200");
        when(service.getInetAddresses()).thenReturn(new InetAddress[]{IP});
        when(service.getPort()).thenReturn(80);
        when(service.getName()).thenReturn("175000133");
        discover.createResult(service);
    }

    @Test
    public void testJSONParser() {
        String json = "{\"rsc\":2000,\"rqi\":\"c78b60079fb05f10\",\"to\":\"/OpenHab\",\"fr\":\"/[0]/MNAE/2/Operation/TargetTemperature/la\",\"pc\":{\"m2m:cin\":{\"rn\":\"00000002\",\"ri\":\"0042_00000002\",\"pi\":\"0042\",\"ty\":4,\"ct\":\"20190819T212846Z\",\"lt\":\"20190819T212846Z\",\"st\":2,\"con\":46.0000000000000000}}}";
        JsonElement obj = new JsonParser().parse(json);
        Optional<JsonElement> result = handler.getJsonPath(obj.getAsJsonObject(), false, "pc", "m2m:cin", "con");
        System.out.println(result);
        Assert.assertTrue(result.isPresent());
        Assert.assertTrue(result.get().isJsonPrimitive());
    }

}