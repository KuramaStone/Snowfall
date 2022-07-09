package me.brook.selection.tools.cuda;

import static jcuda.driver.JCudaDriver.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdeviceptr;
import jcuda.driver.CUfunction;
import jcuda.driver.CUlimit;
import jcuda.driver.CUmodule;
import jcuda.driver.JCudaDriver;
import me.brook.selection.tools.QuadSortable;
import me.brook.selection.tools.Vector2;

public class HormoneManager {

	private CUmodule module;
	private CUfunction calcFunction;
	private CUdeviceptr deviceInputs, deviceOutputs;

	private int totalElements;
	private int totalAgents;
	private int elementsPerAgentEntry;
	private int worldSize;

	private Pointer calcParams;
	private int totalOutputs;

	public HormoneManager() {
		// Enable exceptions and omit all subsequent error checks
		JCudaDriver.setExceptionsEnabled(true);

		// Initialize the driver and create a context for the first device.
		cuInit(0);
		CUdevice device = new CUdevice();
		cuDeviceGet(device, 0);
		CUcontext context = new CUcontext();
		cuCtxCreate(context, 0, device);

		module = new CUmodule();
		loadPTX("JCudaKernelHormones");

		calcFunction = new CUfunction();
		cuModuleGetFunction(calcFunction, module, "calculate");

		JCudaDriver.cuCtxSetLimit(CUlimit.CU_LIMIT_PRINTF_FIFO_SIZE, 4096);
	}
	
	public static void main(String[] args) {
		HormoneManager hm = new HormoneManager();

		int totalAgents = 1000;

		long start = System.nanoTime();
		int worldSize = 1000;
		int hormones = 4;
		float[] inputs = new float[totalAgents * (hormones + 2)];

		Random random = new Random(1234);
		// get 5 closest agents
		for(int i = 0; i < totalAgents; i++) {
			inputs[i * (2 + hormones) + (0)] = random.nextFloat() * worldSize;
			inputs[i * (2 + hormones) + (1)] = random.nextFloat() * worldSize;

			for(int j = 0; j < hormones; j++) {
				inputs[i * (2 + hormones) + (2 + j)] = random.nextFloat() * 50;
			}

		}

		hm.allocateAndPushData(inputs, worldSize, totalAgents, hormones);
		hm.calculate();

		float[] output = hm.getOutputs();
		long time = System.nanoTime() - start;
		System.out.println("Took " + (time / 1.0e9) + " seconds.");

		// draw image from output

		BufferedImage map = new BufferedImage(worldSize, worldSize, BufferedImage.TYPE_INT_RGB);

		for(int i = 0; i < output.length / hormones; i++) {
			int x = i % worldSize;
			int y = i / worldSize;

			float r = output[i * hormones + 0];
			float g = output[i * hormones + 1];
			float b = output[i * hormones + 2];

			r = Math.max(0, Math.min(1, r));
			g = Math.max(0, Math.min(1, g));
			b = Math.max(0, Math.min(1, b));

			int rgb = new Color(r, g, b).getRGB();

			map.setRGB(x, y, rgb);
		}

		try {
			ImageIO.write(map, "png", new File("C:\\Users\\Brookie\\Documents\\Programming\\Eclipse\\workspace2021\\World Simulation\\assets\\kernels\\pic.png"));
		}
		catch(IOException e) {
			e.printStackTrace();
		}

	}

	public void loadPTX(String fileName) {
		cuModuleLoad(module, "/assets/kernels/" + fileName + ".ptx");
	}

	public void allocateAndPushData(float[] inputs, int worldSize, int agents, int hormones) {
		// convert 3d array to 1d array
		this.totalAgents = agents;
		this.elementsPerAgentEntry = 2 + hormones; // x/y + hormone values
		this.totalOutputs = worldSize * worldSize * hormones; // [area] x dimensions
		this.worldSize = worldSize;

		this.totalElements = inputs.length;
		this.deviceInputs = new CUdeviceptr();
		allocateArray(this.deviceInputs, inputs);

		deviceOutputs = new CUdeviceptr();
		JCudaDriver.cuMemAlloc(deviceOutputs, totalOutputs * Sizeof.FLOAT);

		// Set up the kernel parameters: A pointer to an array
		// of pointers which point to the actual values.
		this.calcParams = Pointer.to(
				Pointer.to(this.deviceInputs),
				Pointer.to(new int[] { totalAgents }),
				Pointer.to(new int[] { elementsPerAgentEntry }),
				Pointer.to(new int[] { hormones }),
				Pointer.to(new int[] { worldSize }),
				Pointer.to(this.deviceOutputs));

	}

	public float[] getOutputs() {
		// Allocate host output memory and copy the device output
		// to the host.
		float hostOutput[] = new float[totalOutputs];
		cuMemcpyDtoH(Pointer.to(hostOutput), this.deviceOutputs,
				totalOutputs * Sizeof.FLOAT);

		return hostOutput;
	}

	public void cleanup() {
		JCudaDriver.cuMemFreeHost(this.deviceInputs);
	}

	public void calculate() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) (worldSize * worldSize) / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.calcFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.calcParams, null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	private int allocateArray(CUdeviceptr device, float[] array) {
		int a = JCudaDriver.cuMemAlloc(device, array.length * Sizeof.FLOAT);
		int b = cuMemcpyHtoD(device, Pointer.to(array),
				array.length * Sizeof.FLOAT);
		return a + b;
	}

}
