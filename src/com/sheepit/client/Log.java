/*
 * Copyright (C) 2011-2014 Laurent CLOUET
 * Author Laurent CLOUET <laurent.clouet@nopnop.net>
 *
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.sheepit.client;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Log {
	private static Log instance = null;
	
	private final Map<Integer, ArrayList<String>> checkpoints = new HashMap<>();
	private int lastCheckPoint;
	private final DateFormat dateFormat;
	
	private final boolean printStdOut;
	
	private Log(boolean print_) {
		this.printStdOut = print_;
		this.lastCheckPoint = 0;
		this.checkpoints.put(this.lastCheckPoint, new ArrayList<String>());
		this.dateFormat = new SimpleDateFormat("dd-MM kk:mm:ss");
	}
	
	public void debug(String msg_) {
		this.append("debug", msg_);
	}
	
	public void info(String msg_) {
		this.append("info", msg_);
	}
	
	public void error(String msg_) {
		this.append("error", msg_);
	}
	
	private void append(String level_, String msg_) {
		if (!msg_.isEmpty()) {
			String line = this.dateFormat.format(new java.util.Date()) + " (" + level_ + ") " + msg_;
			if (this.checkpoints.containsKey(this.lastCheckPoint)) {
				this.checkpoints.get(this.lastCheckPoint).add(line);
			}
			if (this.printStdOut) {
				System.out.println(line);
			}
		}
	}
	
	public int newCheckPoint() {
		int time = (int) (new Date().getTime());
		this.checkpoints.put(time, new ArrayList<String>());
		this.lastCheckPoint = time;
		return this.lastCheckPoint;
	}
	
	public ArrayList<String> getForCheckPoint(int point_) {
		return this.checkpoints.get(point_);
	}
	
	public void removeCheckPoint(int point_) {
		try {
			this.checkpoints.remove(point_);
		}
		catch (UnsupportedOperationException e) {
		}
	}
	
	public static synchronized Log getInstance(Configuration config) {
		if (instance == null) {
			instance = new Log(config != null && config.getPrintLog());
		}
		return instance;
	}
	
	public static synchronized void printCheckPoint(int point_) {
		Log log = Log.getInstance(null);
		ArrayList<String> logs = log.getForCheckPoint(point_);
		for (String log1 : logs) {
			System.out.println(log1);
		}
	}
}
