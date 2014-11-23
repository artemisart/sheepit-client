/*
 * Copyright (C) 2010-2014 Laurent CLOUET
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

package com.sheepit.client.standalone;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import static org.kohsuke.args4j.ExampleMode.REQUIRED;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import com.sheepit.client.Client;
import com.sheepit.client.Configuration;
import com.sheepit.client.Configuration.ComputeType;
import com.sheepit.client.Gui;
import com.sheepit.client.Log;
import com.sheepit.client.Pair;
import com.sheepit.client.ShutdownHook;
import com.sheepit.client.hardware.gpu.GPU;
import com.sheepit.client.hardware.gpu.GPUDevice;
import com.sheepit.client.network.ProxyAuthenticator;

public class Worker {
	@Option(name = "--server", aliases = {"-s"}, usage = "Render-farm server, default https://www.sheepit-renderfarm.com", metaVar = "URL")
	private String server = "https://www.sheepit-renderfarm.com";

	@Option(name = "--login", aliases = {"-l"}, usage = "User's login", metaVar = "USERNAME")
	private String login = "";

	@Option(name = "--password", aliases = {"-p"}, usage = "User's password", metaVar = "PASSWORD")
	private String password = "";

	@Option(name = "--cache-dir", aliases = {"-cd"}, usage = "Cache/Working directory. Caution, everything in it not related to the render-farm will be removed", metaVar = "/tmp/cache")
	private String cache_dir = null;

	@Option(name = "-max-uploading-job", usage = "", metaVar = "1")
	private int max_upload = -1;

	@Option(name = "--gpu", aliases = {"-g"}, usage = "CUDA name of the GPU used for the render, for example CUDA_0", metaVar = "CUDA_0")
	private String gpu_device = null;

	@Option(name = "--compute-method", aliases = {"-m"}, usage = "CPU: only use cpu, GPU: only use gpu, CPU_GPU: can use cpu OR gpu, if -gpu is not set it will not use the gpu", metaVar = "CPU_GPU")
	private String method = null;

	@Option(name = "--cores", usage = "Number of core/thread to use for the render", metaVar = "3")
	private int nb_cores = -1;

	@Option(name = "--verbose", aliases = {"-log"}, usage = "Display log")
	private boolean print_log = false;

	@Option(name = "--request-time", usage = "H1:M1-H2:M2,H3:M3-H4:M4 Use the 24h format\nFor example to request job between 2am-8.30am and 5pm-11pm you should do --request-time 2:00-8:30,17:00-23:00\nCaution, it's the requesting job time to get a project not the working time", metaVar = "2:00-8:30,17:00-23:00")
	private String request_time = null;

	@Option(name = "--proxy", usage = "URL of the proxy", metaVar = "http://login:passwd@host:port")
	private String proxy = null;

	@Option(name = "--extras", aliases = {"-e"}, usage = "Extras data push on the authentication request")
	private String extras = null;

	@Option(name = "--oneline", aliases = {"-ol"}, usage="Use oneliner interface")
	private boolean ui_oneline = false;

	@Option(name = "--version", aliases = {"-v"}, usage = "Display application version")
	private boolean display_version = false;

	@Option(name = "--config", aliases = {"-c"}, usage = "Specify a config file with the options in it.\nSee README for syntax and samples.", metaVar = "sample.conf")
	private String config_file = null;

	public static void main(String[] args) {
		new Worker().doMain(args);
	}

	public void doMain(String[] args_) {
		CmdLineParser parser = new CmdLineParser(this);
		if (args_.length == 0) {
			parser.printUsage(System.out);
			return;
		}
		try {
			ArrayList args = new ArrayList(Arrays.asList(args_));
			for (int i = 0; i < args.size(); i++) {
				if (args.get(i).equals("-c") || args.get(i).equals("--config")) {
					args.remove(i);
					for (String line : Files.readAllLines(Paths.get((String)args.remove(i)), StandardCharsets.UTF_8))
						args.add(i++, "--" + line);
				}
			}
			parser.parseArgument(String.join(" ", args).split("\\s+"));
		}
		catch (IOException e) {
			System.err.println(e);
			return;
		}
		catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("Usage: ");
			parser.printUsage(System.err);
			System.err.println();
			System.err.println("Example: java " + this.getClass().getName() + " " + parser.printExample(REQUIRED));
			return;
		}

		if (display_version) {
			System.out.println("Version: " + new Configuration(null, "", "").getJarVersion());
			return;
		}

		ComputeType compute_method = ComputeType.CPU_GPU;
		Configuration config = new Configuration(null, login, password);
		config.setPrintLog(print_log);

		if (cache_dir != null) {
			File a_dir = new File(cache_dir);
			if (a_dir.isDirectory() && a_dir.canWrite()) {
				config.setCacheDir(a_dir);
			}
		}

		if (max_upload != -1) {
			if (max_upload <= 0) {
				System.err.println("Error: max upload should be a greater than zero");
				return;
			}
			config.setMaxUploadingJob(max_upload);
		}

		if (gpu_device != null) {
			String cuda_str = "CUDA_";
			if (gpu_device.startsWith(cuda_str) == false) {
				System.err.println("CUDA_DEVICE should look like 'CUDA_X' where X is a number");
				return;
			}
			try {
				Integer.parseInt(gpu_device.substring(cuda_str.length()));
			}
			catch (NumberFormatException en) {
				System.err.println("CUDA_DEVICE should look like 'CUDA_X' where X is a number");
				return;
			}
			GPUDevice gpu = GPU.getGPUDevice(gpu_device);
			if (gpu == null) {
				System.err.println("GPU unknown");
				System.exit(2);
			}
			config.setUseGPU(gpu);
		}

		if (request_time != null) {
			String[] intervals = request_time.split(",");
			if (intervals != null) {
				config.requestTime = new LinkedList<Pair<Calendar, Calendar>>();

				SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
				for (String interval : intervals) {
					String[] times = interval.split("-");
					if (times != null && times.length == 2) {
						Calendar start = Calendar.getInstance();
						Calendar end = Calendar.getInstance();

						try {
							start.setTime(timeFormat.parse(times[0]));
							end.setTime(timeFormat.parse(times[1]));
						}
						catch (ParseException e) {
							System.err.println("Error: wrong format in request time");
							System.exit(2);
						}

						if (start.before(end)) {
							config.requestTime.add(new Pair<Calendar, Calendar>(start, end));
						}
						else {
							System.err.println("Error: wrong request time " + times[0] + " is after " + times[1]);
							System.exit(2);
						}
					}
				}
			}
		}

		if (nb_cores < -1) {
			System.err.println("Error: use-number-core should be a greater than zero");
			return;
		}
		else {
			config.setUseNbCores(nb_cores);
		}

		if (method != null) {
			if (method.equalsIgnoreCase("cpu")) {
				compute_method = ComputeType.CPU_ONLY;
			}
			else if (method.equalsIgnoreCase("gpu")) {
				compute_method = ComputeType.GPU_ONLY;
			}
			else if (method.equalsIgnoreCase("cpu_gpu") || method.equalsIgnoreCase("gpu_cpu")) {
				compute_method = ComputeType.CPU_GPU;
			}
			else {
				System.err.println("Error: compute-method unknown");
				System.exit(2);
			}
		}

		if (proxy != null) {
			try {
				URL url = new URL(proxy);
				String userinfo = url.getUserInfo();
				if (userinfo != null) {
					String[] elements = userinfo.split(":");
					if (elements.length == 2) {
						String proxy_user = elements[0];
						String proxy_password = elements[1];

						if (proxy_user != null && proxy_password != null) {
							Authenticator.setDefault(new ProxyAuthenticator(proxy_user, proxy_password));
						}
					}
				}

				System.setProperty("http.proxyHost", url.getHost());
				System.setProperty("http.proxyPort", Integer.toString(url.getPort()));

				System.setProperty("https.proxyHost", url.getHost());
				System.setProperty("https.proxyPort", Integer.toString(url.getPort()));
			}
			catch (MalformedURLException e) {
				System.err.println("Error: wrong url for proxy");
				System.err.println(e);
				System.exit(2);
			}
		}

		if (extras != null) {
			config.setExtras(extras);
		}

		if (compute_method == ComputeType.CPU_ONLY) { // the client was to render with cpu but on the server side project type are cpu+gpu or gpu prefered but never cpu only
			compute_method = ComputeType.CPU_GPU;
			config.setComputeMethod(compute_method);
			config.setUseGPU(null); // remove the GPU
		}
		else {
			config.setComputeMethod(compute_method); // doing it here because it have to be done after the setUseGPU
		}

		Log.getInstance(config).debug("client version " + config.getJarVersion());

		if (ui_oneline && config.getPrintLog()) {
			System.out.println("OneLine ui can not be used if the verbose mode is enable");
			System.exit(2);
		}
		Client cli = new Client(ui_oneline ? new GuiTextOneLine() : new GuiText(), config, server);

		new ShutdownHook(cli).attachShutDownHook();

		cli.run();
		cli.stop();
	}
}
