package me.brook.selection;

import static jcuda.driver.JCudaDriver.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import javax.swing.JFrame;

import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdeviceptr;
import jcuda.driver.CUfunction;
import jcuda.driver.CUlimit;
import jcuda.driver.CUmodule;
import jcuda.driver.JCudaDriver;

public class PressureMap {

	private CUmodule module;
	private CUfunction diffuseFunction, updateFunction, moveFunction;
	private CUdeviceptr deviceInputs, deviceOutputs;


	private Pointer diffuseParams, updateParams, moveParams;
	private int worldWidth, worldHeight;

	public PressureMap() {
		// Enable exceptions and omit all subsequent error checks
		JCudaDriver.setExceptionsEnabled(true);

		// Initialize the driver and create a context for the first device.
		cuInit(0);
		CUdevice device = new CUdevice();
		cuDeviceGet(device, 0);
		CUcontext context = new CUcontext();
		cuCtxCreate(context, 0, device);

		module = new CUmodule();
		loadPTX("JCudaKernelPressure");

		diffuseFunction = new CUfunction();
		cuModuleGetFunction(diffuseFunction, module, "diffuse");

		updateFunction = new CUfunction();
		cuModuleGetFunction(updateFunction, module, "update");

		moveFunction = new CUfunction();
		cuModuleGetFunction(moveFunction, module, "move");

		JCudaDriver.cuCtxSetLimit(CUlimit.CU_LIMIT_PRINTF_FIFO_SIZE, 4096);
	}

	public static void main(String[] args) {
		Random random = new Random(1234);
		PressureMap pm = new PressureMap();

		int worldSize = 512;
		int entries = 6;
		float[] inputs = new float[worldSize * worldSize * entries];
		for(int i = 0; i < inputs.length; i += (entries)) {
			int y = i / worldSize;
			float vx = 0;
			float vy = 0;
			if(y == 0)
				vy = 1;
			
			inputs[i] = random.nextFloat(); // density
			inputs[i + 1] = vx; // vx
			inputs[i + 2] = vy; // vy
			inputs[i + 3] = inputs[i]; // previous density
			inputs[i + 4] = vx; // vx
			inputs[i + 5] = vy; // vy
		}

		pm.allocateAndPushData(inputs, entries, worldSize, worldSize);

		JFrame frame = new JFrame();
		frame.setSize(worldSize, worldSize);
		frame.setResizable(false);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);

		BufferedImage buffer = new BufferedImage(worldSize, worldSize, BufferedImage.TYPE_INT_RGB);

		Graphics2D g2 = (Graphics2D) buffer.getGraphics();

		long last = System.nanoTime();
		long nsWaiting = 0;
		long frames = 0;
		while(true) {
//			pm.diffuse();
//			pm.update();
			pm.move();
			pm.update();
			float[] out = pm.getOutputs();

			for(int x = 0; x < worldSize; x++) {
				for(int y = 0; y < worldSize; y++) {
					int id = x + y * worldSize;
					float d = out[id] / 1;
					d = Math.max(0, Math.min(1, d));
					
					int rgb = new Color(d, d, d).getRGB();
					
					buffer.setRGB(x, y, rgb);
				}
			}

			long now = System.nanoTime();
			nsWaiting = now - last;
			last = now;
			frames++;
			System.out.println(nsWaiting / frames + "ns per frame");
			
			frame.getGraphics().drawImage(buffer, 0, 0, null);
		}

	}

	public void loadPTX(String fileName) {
		cuModuleLoad(module, "/assets/kernels/" + fileName + ".ptx");
	}

	public void allocateAndPushData(float[] pressureMap, int entries, int worldWidth, int worldHeight) {
		this.worldWidth = worldWidth;
		this.worldHeight = worldHeight;

		this.deviceInputs = new CUdeviceptr();
		allocateArray(deviceInputs, pressureMap);

		this.deviceOutputs = new CUdeviceptr();
		JCudaDriver.cuMemAlloc(deviceOutputs, (worldWidth * worldHeight) * Sizeof.FLOAT);

		this.diffuseParams = Pointer.to(
				Pointer.to(new int[] { worldWidth }),
				Pointer.to(new int[] { worldHeight }),
				Pointer.to(new int[] { entries }),
				Pointer.to(this.deviceInputs),
				Pointer.to(this.deviceOutputs));
		this.updateParams = Pointer.to(
				Pointer.to(new int[] { worldWidth }),
				Pointer.to(new int[] { worldHeight }),
				Pointer.to(new int[] { entries }),
				Pointer.to(this.deviceInputs),
				Pointer.to(this.deviceOutputs));
		this.moveParams = Pointer.to(
				Pointer.to(new int[] { worldWidth }),
				Pointer.to(new int[] { worldHeight }),
				Pointer.to(new int[] { entries }),
				Pointer.to(this.deviceInputs));
	}

	public float[] getOutputs() {
		// Allocate host output memory and copy the device output
		// to the host.
		float hostOutput[] = new float[worldWidth * worldHeight];
		cuMemcpyDtoH(Pointer.to(hostOutput), this.deviceOutputs,
				hostOutput.length * Sizeof.FLOAT);

		return hostOutput;
	}

	public void cleanup() {
		JCudaDriver.cuMemFreeHost(this.deviceInputs);
		JCudaDriver.cuMemFreeHost(this.deviceOutputs);
	}

	public void diffuse() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) (worldWidth * worldHeight) / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.diffuseFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.diffuseParams, null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	public void move() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) (worldWidth * worldHeight) / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.moveFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.moveParams, null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	public void update() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) (worldWidth * worldHeight) / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.updateFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.updateParams, null // Kernel- and extra parameters
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
