package hk.samwong.roomservice.servlet.helper;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

public class MapUtils{

	public static String printParameterMap(Map<String, String[]> parameters) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		for(Entry<String, String[]> entry : parameters.entrySet()){
			sb.append("[" + entry.getKey() + "->" + Arrays.toString(entry.getValue()) + "]");
		}
		sb.append("}");
		return sb.toString();
	}
	
}
