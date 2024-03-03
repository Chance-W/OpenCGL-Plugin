package com.xtool.opencgl.utils;


import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Chance.W
 * @date 2020/2/7-10:06
 */
public class MMLAssert {
    public MMLAssert() {
    }

    public static MMLACK parseReturnMessage(String msg) throws Exception {
        MMLACK mmlAck = new MMLACK();
        mmlAck.setOrignal(msg);
     //   int i_s = false;
        String t_msg = "";
        int i_s = msg.indexOf(":");
        if (i_s > -1) {
            mmlAck.setAck(msg.substring(0, i_s));
            t_msg = msg.substring(i_s + 1);
        }

        i_s = t_msg.indexOf(":");
        if (i_s > -1) {
            mmlAck.setCmd(t_msg.substring(0, i_s));
            t_msg = t_msg.substring(i_s + 1);
        }

        i_s = t_msg.indexOf(",");
        if (i_s > -1) {
            mmlAck.setCode(t_msg.substring(0, i_s).replace("RETN=", ""));
        }

        String[] parseResult = preDeal(t_msg).split("\\^");
        Map<String, Object> hashMap = new LinkedHashMap<>();

        for(int i = 0; i < parseResult.length; ++i) {
            int indexEqual = parseResult[i].indexOf("=");
            if (indexEqual == -1) {
                throw new Exception("Return Message Error,attribute and attribute value are separated by '='");
            }

            String key = parseResult[i].substring(0, indexEqual);
            String value = parseResult[i].substring(indexEqual + 1);
            if ("ATTR".toUpperCase().equals(key.trim().toUpperCase())) {
                if (i + 1 >= parseResult.length) {
                    throw new Exception("Return Message format error, please make sure whether the key ATTR and RESULT appear at the same time, otherwise all ATTRs value are set Empty string.;" + msg);
                }

                int indexResult = parseResult[i + 1].indexOf("=");
                if (indexResult == -1) {
                    throw new Exception("Return Message Error,attribute and attribute value are separated by '=';" + msg);
                }

                String resultKey = parseResult[i + 1].substring(0, indexResult);
                String resultValue = parseResult[i + 1].substring(indexResult + 1);
                if (!"RESULT".equals(resultKey.trim().toUpperCase())) {
                    throw new Exception("Return Message format error, the key ATTR and RESULT must appear at the same time;" + msg);
                }

                if (value.split("\\|").length == 1) {
                    hashMap.put("ATTR", "[{" + value + "=" + resultValue + "}]");
                } else {
                    String[] attrs = value.split("\\|");
                    int attrLenth = attrs.length;
                    String[] values = resultValue.split("\\&");
                    int valueTimes = values.length;
                    if (valueTimes == 1) {
                        String[] attrValue = values[0].split("\\|");
                        String[] rts = new String[attrLenth];

                        int index;
                        for(index = 0; index < attrLenth; ++index) {
                            rts[index] = "";
                        }

                        for(index = 0; index < attrLenth; ++index) {
                            if (index < attrValue.length) {
                                rts[index] = attrValue[index];
                            }

                            hashMap.put(attrs[index].trim().toUpperCase(), rts[index]);
                        }
                    } else {
                        List<Map<String, String>> attrResult = new LinkedList<>();
                        int time = 0;

                        while(true) {
                            if (time >= valueTimes) {
                                hashMap.put("ATTR", attrResult);
                                break;
                            }

                            Map<String, String> attrValues = new LinkedHashMap<>();
                            String[] attrValue = values[time].split("\\|");
                            String[] rts = new String[attrLenth];

                            int index;
                            for(index = 0; index < attrLenth; ++index) {
                                rts[index] = "";
                            }

                            for(index = 0; index < attrLenth; ++index) {
                                if (index < attrValue.length) {
                                    rts[index] = attrValue[index];
                                }

                                attrValues.put(attrs[index].trim().toUpperCase(), rts[index]);
                            }

                            attrResult.add(attrValues);
                            ++time;
                        }
                    }
                }

                ++i;
            } else {
                hashMap.put(key.trim().toUpperCase(), value);
            }
        }

        mmlAck.setCheckMap(hashMap);
        return mmlAck;
    }

    private static String preDeal(String message) {
        String dealedMessage = "";
        String msg = message;
        String str = "";

        while(msg.length() != 0) {
            int index = msg.indexOf(44);
            int indexEqual;
            if (index > -1) {
                str = msg.substring(0, index);
                indexEqual = str.indexOf(61);
                msg = msg.substring(index + 1);
                if (indexEqual != -1) {
                    dealedMessage = dealedMessage + str + "^";
                } else {
                    dealedMessage = dealedMessage.substring(0, dealedMessage.length() - 1);
                    if (msg.length() == 0) {
                        dealedMessage = dealedMessage + "," + str;
                    } else {
                        dealedMessage = dealedMessage + "," + str + "^";
                    }
                }
            } else {
                indexEqual = msg.indexOf(61);
                if (indexEqual != -1) {
                    dealedMessage = dealedMessage + msg;
                } else {
                    dealedMessage = dealedMessage.substring(0, dealedMessage.length() - 1);
                    dealedMessage = dealedMessage + "," + msg;
                }

                msg = "";
            }
        }

        return dealedMessage;
    }

    public static void main(String[] args) {
        String ret = "ACK:LIST PHS LASTACCTOPER:RETN=0000,DESC=SUCCESS,ATTR=DN|SEQ|OPERTYPE|BEFOROPER|AMOUNT|AFTEROPER|TRADETIME|PARTYCODE|RECHARGETYPE|ADJUSTREASON,RESULT=861391390447|1|P|5000|10000|15000|2013-03-04 21:37:21|1|ETopup|&861391390447|2|P|5000|10000|15000|2013-03-03 21:40:54|1|ETopup|&861391390447|3|P|5000|10000|15000|2013-02-21 21:12:45||Cash|&861391390447|4|P|5000|10000|15000|2013-02-18 20:55:20||Cash|&861391390447|5|P|5000|10000|15000|2013-02-07 21:16:05||Cash|";

        try {
            System.out.println(parseReturnMessage(ret).getCheckMap());
        } catch (Exception var3) {
            var3.printStackTrace();
        }

    }
}
