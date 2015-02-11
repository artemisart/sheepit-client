/*
 * Copyright (C) 2013-2014 Laurent CLOUET
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

package com.sheepit.client.hardware.gpu;

import java.util.LinkedList;
import java.util.List;

import com.sheepit.client.os.OS;
import com.sun.jna.Native;

public class GPU {
	public static List<GPUDevice> devices = null;
	
	public static boolean generate() {
		OS os = OS.getOS();
		String path = os.getCUDALib();
		if (path == null) {
			System.out.println("GPU::generate no CUDA lib path found");
			return false;
		}
		CUDA cudalib = null;
		try {
			cudalib = (CUDA) Native.loadLibrary(path, CUDA.class);
		}
		catch (java.lang.UnsatisfiedLinkError e) {
			System.out.println("GPU::generate failed to load CUDA lib (path: " + path + ")");
			return false;
		}
		catch (java.lang.ExceptionInInitializerError e) {
			System.out.println("GPU::generate ExceptionInInitializerError " + e);
			return false;
		}
		catch (Exception e) {
			System.out.println("GPU::generate generic exception " + e);
			return false;
		}
		
		int result = cudalib.cuInit(0);
		if (result != CUresult.CUDA_SUCCESS) {
			System.out.println("GPU::generate cuInit failed (ret: " + result + ")");
			return false;
		}
		
		if (result == CUresult.CUDA_ERROR_NO_DEVICE) {
			return false;
		}
		
		int[] count = new int[1];
		result = cudalib.cuDeviceGetCount(count);
		
		if (result != CUresult.CUDA_SUCCESS) {
			System.out.println("GPU::generate cuDeviceGetCount failed (ret: " + CUresult.stringFor(result) + ")");
			return false;
		}
		
		devices = new LinkedList<>();
		
		for (int num = 0; num < count[0]; num++) {
			byte name[] = new byte[256];
			
			result = cudalib.cuDeviceGetName(name, 256, num);
			if (result != CUresult.CUDA_SUCCESS) {
				System.out.println("GPU::generate cuDeviceGetName failed (ret: " + CUresult.stringFor(result) + ")");
				continue;
			}
			
			long[] ram = new long[1];
			result = cudalib.cuDeviceTotalMem(ram, num);
			
			if (result != CUresult.CUDA_SUCCESS) {
				System.out.println("GPU::generate cuDeviceTotalMem failed (ret: " + CUresult.stringFor(result) + ")");
				return false;
			}
			
			devices.add(new GPUDevice(new String(name).trim(), ram[0], "CUDA_" + Integer.toString(num)));
		}
		return true;
	}
	
	public static List<String> listModels() {
		if (devices == null) {
			generate();
		}
		if (devices == null) {
			return null;
		}
		
		List<String> devs = new LinkedList<>();
		for (GPUDevice dev : devices) {
			devs.add(dev.getModel());
		}
		return devs;
	}
	
	public static List<GPUDevice> listDevices() {
		if (devices == null) {
			generate();
		}
		if (devices == null) {
			return null;
		}
		
		return devices;
	}
	
	public static GPUDevice getGPUDevice(String device_model) {
		if (device_model == null) {
			return null;
		}
		
		if (devices == null) {
			generate();
		}
		
		if (devices == null) {
			return null;
		}
		
		for (GPUDevice dev : devices) {
			if (device_model.equals(dev.getCudaName()) || device_model.equals(dev.getModel())) {
				return dev;
			}
		}
		return null;
	}
}
