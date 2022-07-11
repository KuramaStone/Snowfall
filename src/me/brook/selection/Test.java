package me.brook.selection;

import java.util.HashMap;
import java.util.Map;

import org.nustaq.serialization.FSTConfiguration;

public class Test {

	public static void main(String[] args) {

		FSTConfiguration conf = FSTConfiguration.createJsonConfiguration(true, false);
		
		Map<String, Object> map = new HashMap<>();
		map.put("data", "null");
		map = (Map<String, Object>) conf.asObject(conf.asByteArray(map));
		
		System.out.println(map.get("data") == null);
	}

}
