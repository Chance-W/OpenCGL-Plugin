package com.opencgl.lanmsg.core;

import java.net.*;
import java.util.*;

public final class NetworkAddresses {
    public record Address(String name,String ip,String broadcast,boolean virtual) {
        @Override public String toString() { return name+" · "+ip+(broadcast.isEmpty()?"":" → "+broadcast); }
    }
    public static List<Address> list() {
        var result=new ArrayList<Address>();
        try {
            for(var ni:Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if(!ni.isUp()||ni.isLoopback())continue;
                for(var a:ni.getInterfaceAddresses()) if(a.getAddress() instanceof Inet4Address)
                    result.add(new Address(ni.getDisplayName(),a.getAddress().getHostAddress(),a.getBroadcast()==null?"":a.getBroadcast().getHostAddress(),ni.isVirtual()));
            }
        }catch(SocketException ignored) { }
        result.sort(Comparator.comparing(Address::virtual).thenComparing(Address::name));
        if(result.isEmpty())result.add(new Address("Loopback","127.0.0.1","",false));
        return result;
    }
    private NetworkAddresses() {}
}
