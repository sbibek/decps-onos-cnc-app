/*
 * Copyright 2019-present Open Networking Foundation
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
package org.decps.app;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.glassfish.jersey.internal.jsr166.Flow;
import org.onlab.packet.*;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.*;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.awt.X11.XSystemTrayPeer;

import java.util.*;

import static org.onlab.util.Tools.get;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {

    private final int mode = 1; // 0 => hub, 1 => switch

    private final Logger log = LoggerFactory.getLogger(getClass());

    private FlowAnalytics analytics = new FlowAnalytics();
    private BotsInfo botsInfo = new BotsInfo();
    private PacketThrottle packetThrottle = new PacketThrottle();
    /** Some configurable property. */
    private String someProperty;

    private Integer EXP_RANDOM = 0;
    private Integer EXP_WEIGHTED = 1;
    private Integer EXP_GROUP = 2;


     Integer bcount = 0;
     long encounteredOn=0;
    private List<Integer> weightedList = new ArrayList<>();

    private Integer EXPERIMENT = EXP_RANDOM;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;


    protected Map<DeviceId, Map<MacAddress, PortNumber>>  mactables = Maps.newConcurrentMap();
    private ApplicationId appId;
    private PacketProcessor processor;


    // for grouping logic
    int totalPkts =0 ;
    int packetCountTracker = 0;
    boolean reject = false;
    int groupMembersCount = 10;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        log.info("Started");
        appId = coreService.getAppId("org.decps.app2");
        log.info("(application id, name)  " + appId.id()+", " + appId.name());

        processor = new SwitchPacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(3));

        // now lets restrict packet to ipv4 and arp
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.empty());


        // initialize the weighted list
        // current, 1:50%, 0: 50%
        weightedList.add(1);
        weightedList.add(0);
        weightedList.add(1);
        weightedList.add(0);
        weightedList.add(1);
        weightedList.add(0);
        weightedList.add(1);
        weightedList.add(0);
        weightedList.add(1);
        weightedList.add(0);
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
        packetService.removeProcessor(processor);
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured for experiment "+EXPERIMENT);
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }


    private class SwitchPacketProcessor implements PacketProcessor {

	public int getrandom(int max, int min){
		Random r = new Random();
		return r.nextInt((max-min)+1)+min;
	}

	public void randomDrops(PacketContext context){
	 	InboundPacket iPacket = context.inPacket();
             	Ethernet ethPacket = iPacket.parsed();

		if(ethPacket.getEtherType() == Ethernet.TYPE_IPV4)  {
			IPv4 ipPacket = (IPv4) ethPacket.getPayload();	
			if(ipPacket.getProtocol() == IPv4.PROTOCOL_TCP) {
				TCP tcpPacket = (TCP)ipPacket.getPayload();
				if(IPv4.fromIPv4Address(ipPacket.getSourceAddress()).equalsIgnoreCase("134.197.42.83") 
						&& tcpPacket.getFlags() == 24 
						&& tcpPacket.getPayload().serialize().length > 2){
					if(weightedList.get(getrandom(9,0)) == 0){
						context.block();
						System.out.println("packet blocked");	
						return;
					}
					 else {
						System.out.println("packet allowed");	
						next(context);
						return;
					 }
				}	
			}
		}

		next(context);
	}
        //(40.78.22.17,8883)
        //(40.78.22.17=676206097,8883)
        @java.lang.Override
        public void process(PacketContext context) {
//            next(context);
		randomDrops(context);
//             InboundPacket iPacket = context.inPacket();
//             Ethernet ethPacket = iPacket.parsed();
// //            boolean reject = false;
//             if(ethPacket.getEtherType() == Ethernet.TYPE_IPV4)  {
//                 IPv4 ipPacket = (IPv4) ethPacket.getPayload();
//                 // now current point of interest is just TCP packets
//                 if(ipPacket.getProtocol() == IPv4.PROTOCOL_TCP) {
//                     TCP tcpPacket = (TCP)ipPacket.getPayload();

// //                    if(EXPERIMENT == EXP_GROUP) {
// //                        botsInfo.registerIfBot(ipPacket.getSourceAddress(), tcpPacket.getSourcePort(), ethPacket.getSourceMAC(), ipPacket.getDestinationAddress(), tcpPacket.getDestinationPort(), ethPacket.getDestinationMAC());
// //                    }
//                     System.out.println("("+IPv4.fromIPv4Address(ipPacket.getSourceAddress())+"="+ ipPacket.getSourceAddress()+","+tcpPacket.getSourcePort()+ ") -> (" + IPv4.fromIPv4Address(ipPacket.getDestinationAddress())+","+tcpPacket.getDestinationPort()+")" );
//                     if(ipPacket.getDestinationAddress() == 676206097 && tcpPacket.getDestinationPort() == 8883) {
//                         // now lets filter to the flags
//                         // we just want the data related to PSH ACK as it contains the payload
//                         // also we want the packet to have the data bytes larger than 2 ( learnt from the packet inspection)
//                         if(tcpPacket.getFlags() == 24
//                                 && tcpPacket.getPayload().serialize().length > 2
//                         ) {

// //                            System.out.println("("+IPv4.fromIPv4Address(ipPacket.getSourceAddress())+"="+ ipPacket.getSourceAddress()+","+tcpPacket.getSourcePort()+ ") -> (" + IPv4.fromIPv4Address(ipPacket.getDestinationAddress())+","+tcpPacket.getDestinationPort()+")" );
//                             next(context);
//                         } else {
//                             next(context);
//                         }
//                     } else {
//                         next(context);
//                     }
//                 } else {
//                     next(context);
//                 }
//             } else {
//                 next(context);
//             }
        }

        public void next(PacketContext context){
            initMacTable(context.inPacket().receivedFrom());
            actLikeSwitch(context);
        }

        public void actLikeHub(PacketContext context){
           context.treatmentBuilder().setOutput(PortNumber.FLOOD) ;
           context.send();
        }

        public void actLikeSwitch(PacketContext context) {
            short type =  context.inPacket().parsed().getEtherType();

            ConnectPoint cp = context.inPacket().receivedFrom();
            Map<MacAddress, PortNumber> macTable = mactables.get(cp.deviceId());
            MacAddress srcMac = context.inPacket().parsed().getSourceMAC();
            MacAddress dstMac = context.inPacket().parsed().getDestinationMAC();
            macTable.put(srcMac, cp.port());
            PortNumber outPort = macTable.get(dstMac);

            if(outPort != null) {
//                log.info("("+dstMac+") is a on port "+ outPort + "[ stats: device count #"+mactables.size()+"]");
                context.treatmentBuilder().setOutput(outPort);
                context.send();
            } else {
//                log.info("("+dstMac+") is not yet mapped, so flooding"+ "[ stats: device count #"+mactables.size()+"]");
                // means just flood as we dont have mapping yet
                actLikeHub(context);
            }
        }

        private void initMacTable(ConnectPoint cp){
            mactables.putIfAbsent(cp.deviceId(), Maps.newConcurrentMap());
        }
    }

}
