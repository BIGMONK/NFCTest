package com.youtu.djf.utusbreader;

/**
 * Created by djf on 2018/1/30.
 */

public class EncodeByte2Integer {
    public static Integer getInteger(Byte b) {
        int i = b-29;
        if (i != 10)
            return i;
        else
            return 0;
    }
}
