package org.json;

import net.runelite.mapping.Implements;

@Implements("JSONObject$Null")
final class JSONObject$Null {
	public boolean equals(Object var1) {
		return var1 == null || this == var1;
	}

	public final Object clone() {
		return this;
	}

	public String toString() {
		return "null";
	}
}
