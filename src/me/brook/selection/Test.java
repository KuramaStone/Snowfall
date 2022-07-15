package me.brook.selection;

import java.util.HashMap;
import java.util.Map;

import org.nustaq.serialization.FSTConfiguration;

public class Test {

	public static void main(String[] args) {
		System.out.println(Integer.toBinaryString(getState(true, true, true, false)));
	}

	public static int getState(boolean a, boolean b, boolean c, boolean d) {
		return ((a ? 1 : 0) * 8) + ((b ? 1 : 0) * 4) + ((c ? 1 : 0) * 2) + (d ? 1 : 0);
	}

}
