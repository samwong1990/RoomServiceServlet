package helper;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.builder.ToStringBuilder;

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
