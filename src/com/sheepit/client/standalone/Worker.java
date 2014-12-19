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

import static org.kohsuke.args4j.ExampleMode.REQUIRED;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.sheepit.client.Client;
import com.sheepit.client.Configuration;
import com.sheepit.client.Configuration.ComputeType;
import com.sheepit.client.Log;
import com.sheepit.client.Pair;
import com.sheepit.client.ShutdownHook;
import com.sheepit.client.hardware.gpu.GPU;
import com.sheepit.client.hardware.gpu.GPUDevice;
import com.sheepit.client.network.ProxyAuthenticator;

public class Worker {
	@Option(name = "--server", aliases = "-s", usage = "Render-farm server, default https://www.sheepit-renderfarm.com", metaVar = "URL")
	private String server = "https://www.sheepit-renderfarm.com";

	@Option(name = "--interactive", aliases = "-i", usage = "Enable interactive prompt for login and password")
	private boolean interactive_prompt = false;

	@Option(name = "--login", aliases = "-l", usage = "User's login", metaVar = "USERNAME")
	private String login = "";

	@Option(name = "--password", aliases = "-p", usage = "User's password", metaVar = "PASSWORD")
	private String password = "";

	@Option(name = "--cache-dir", aliases = "-cd", usage = "Cache/Working directory. Caution, everything in it not related to the render-farm will be removed", metaVar = "/tmp/cache")
	private String cache_dir = null;

	@Option(name = "-max-uploading-job", usage = "", metaVar = "1")
	private int max_upload = -1;

	@Option(name = "--gpu", aliases = "-g", usage = "CUDA name of the GPU used for the render, for example CUDA_0", metaVar = "CUDA_0")
	private String gpu_device = null;

	@Option(name = "--compute-method", aliases = "-m", usage = "CPU: only use cpu, GPU: only use gpu, CPU_GPU: can use cpu OR gpu, if -gpu is not set it will not use the gpu", metaVar = "CPU")
	private String method = null;

	@Option(name = "--cores", usage = "Number of core/thread to use for the render", metaVar = "3")
	private int nb_cores = -1;

	@Option(name = "--verbose", aliases = "-log", usage = "Display log")
	private boolean print_log = false;

	@Option(name = "--request-time", usage = "H1:M1-H2:M2,H3:M3-H4:M4 Use the 24h format\nFor example to request job between 2am-8.30am and 5pm-11pm you should do --request-time 2:00-8:30,17:00-23:00\nCaution, it's the requesting job time to get a project not the working time", metaVar = "2:00-8:30,17:00-23:00")
	private String request_time = null;

	@Option(name = "--proxy", usage = "URL of the proxy", metaVar = "http://login:passwd@host:port")
	private String proxy = null;

	@Option(name = "--extras", aliases = "-e", usage = "Extras data push on the authentication request")
	private String extras = null;

	@Option(name = "--oneline", aliases = "-ol", usage = "Use oneline interface")
	private boolean ui_oneline = false;

	@Option(name = "--version", aliases = "-v", usage = "Display application version")
	private boolean display_version = false;

	@Option(name = "--config", aliases = "-c", usage = "Specify a config file with the options in it.\nSee README for syntax and samples.", metaVar = "sample.conf")
	private String config_file = null;

	public static void main(String[] args) {
		new Worker().doMain(args);
	}

	void doMain(String[] args) {
		CmdLineParser parser = new CmdLineParser(this);
		if (args.length == 0) {
			parser.printUsage(System.out);
			return;
		}
		try {
			Matcher re = Pattern.compile("(-c|--config)\\s+(\\S+)").matcher(String.join(" ", args));
			StringBuffer sb = new StringBuffer();
			while (re.find()) {
				re.appendReplacement(sb, "");
				for (String line : Files.readAllLines(Paths.get(re.group(2)), StandardCharsets.UTF_8))
					if (!line.startsWith("#"))
						sb.append('-').append('-').append(line).append(' ');
				re.appendTail(sb);
			}
			parser.parseArgument((sb.length() == 0 ? args : sb.toString().split("\\s+")));
			if (interactive_prompt) {
				if (login.isEmpty())
					login = System.console().readLine("Login:");
				if (password.isEmpty())
					password = new String(System.console().readPassword("Password:"));
			}
		}
		catch (IOException e) {
			e.printStackTrace();
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
			System.out.println("Version: " + new Configuration("", "").getJarVersion());
			return;
		}

		Configuration config = new Configuration(login, password);
		config.setPrintLog(print_log);

		if (cache_dir != null) {
			File a_dir = new File(cache_dir);
			if (!a_dir.exists())
				a_dir.mkdirs();
			if (a_dir.isDirectory() && a_dir.canWrite()) {
				config.setCacheDir(a_dir);
			}
			else {
				System.out.println("Bad cache dir, using default one.");
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
			if (!Pattern.matches("^CUDA_\\d+$", gpu_device)) {
				System.err.println("CUDA_DEVICE must be 'CUDA_X' where X is a number");
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
			config.requestTime = new LinkedList<>();

			SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
			for (String interval : intervals) {
				String[] times = interval.split("-");
				if (times.length == 2) {
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
						config.requestTime.add(new Pair<>(start, end));
					}
					else {
						System.err.println("Error: wrong request time " + times[0] + " is after " + times[1]);
						System.exit(2);
					}
				}
			}
		}
		
		if (nb_cores < -1 || nb_cores == 0) { // -1 is the default
			System.err.println("Error: use-number-core should be a greater than zero");
			return;
		}
		else {
			config.setUseNbCores(nb_cores);
		}

		ComputeType compute_method = ComputeType.CPU_ONLY;
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
		else {
			compute_method = config.getGPUDevice() == null ? ComputeType.CPU_ONLY : ComputeType.GPU_ONLY;
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
				e.printStackTrace();
				System.exit(2);
			}
		}
		
		if (extras != null) {
			config.setExtras(extras);
		}
		
		if (compute_method == ComputeType.CPU_ONLY && config.getGPUDevice() != null) {
			System.err.println("You choose to use the CPU only but a GPU was also provided. You can not do both.");
			System.err.println("Aborting");
			System.exit(2);
		}
		else if (compute_method == ComputeType.CPU_GPU && config.getGPUDevice() == null) {
			System.err.println("You choose to use the CPU and GPU but no GPU device was provided.");
			System.err.println("Aborting");
			System.exit(2);
		}
		else if (compute_method == ComputeType.GPU_ONLY && config.getGPUDevice() == null) {
			System.err.println("You choose to use the GPU only but no GPU device was provided.");
			System.err.println("Aborting");
			System.exit(2);
		}
		else if (compute_method == ComputeType.CPU_ONLY) {
			config.setUseGPU(null); // remove the GPU
		}
		
		config.setComputeMethod(compute_method);
		
		Log.getInstance(config).debug("client version " + config.getJarVersion());
		
		if (ui_oneline && config.getPrintLog()) {
			System.out.println("OneLine ui can not be used if the verbose mode is enabled.");
			System.exit(2);
		}
		Client cli = new Client(ui_oneline ? new GuiTextOneLine() : new GuiText(), config, server);
		
		new ShutdownHook(cli).attachShutDownHook();
		
		cli.run();
		cli.stop();
	}
}
