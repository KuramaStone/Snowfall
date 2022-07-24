package me.brook.selection;

import static jcuda.driver.JCudaDriver.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;

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
	private CUfunction calculateForcesFunction, applyForcesFunction, neighborFunction, renderFunction;
	private CUdeviceptr deviceInputs, deviceOutputs, deviceWorldData, deviceNeighbors;

	private Pointer diffuseParams, calculationParams, renderParams;
	private int worldWidth, worldHeight, entries, maxParticles;

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

		calculateForcesFunction = new CUfunction();
		cuModuleGetFunction(calculateForcesFunction, module, "calculateForces");

		applyForcesFunction = new CUfunction();
		cuModuleGetFunction(applyForcesFunction, module, "applyForces");

		renderFunction = new CUfunction();
		cuModuleGetFunction(renderFunction, module, "render");

		neighborFunction = new CUfunction();
		cuModuleGetFunction(neighborFunction, module, "sortNeighbors");

		JCudaDriver.cuCtxSetLimit(CUlimit.CU_LIMIT_PRINTF_FIFO_SIZE, 4096);
	}

	public static void main(String[] args) {
		Random random = new Random(1234);
		PressureMap pm = new PressureMap();

		int worldSize = 512;
		int entries = 8;
		int maxParticles = 10000;
		float[] inputs = new float[maxParticles * entries];
		int j = 0;
		for(int i = 0; i < maxParticles; i++) {
			float vx = 0;
			float vy = 0;
			float x = (0.1f + random.nextFloat() * 0.8f) * worldSize;
			float y = (0.1f + random.nextFloat() * 0.8f)  * worldSize;

			inputs[j++] = x; // x
			inputs[j++] = y; // y
			inputs[j++] = vx; // vx
			inputs[j++] = vy; // vy
			inputs[j++] = 2; // radius
			inputs[j++] = 1; // density
			inputs[j++] = 0; // vx to add
			inputs[j++] = 0; // vy to add
		}

		pm.allocateAndPushData(inputs, entries, maxParticles, worldSize, worldSize);

		JFrame frame = new JFrame();
		frame.setSize(512, 512);
		frame.setResizable(false);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		LinkedBlockingQueue<Runnable> runLater = new LinkedBlockingQueue<>();

		JPanel panel = new JPanel();
		frame.setContentPane(panel);
		frame.setVisible(true);
		MouseAdapter ma = new MouseAdapter() {

			@Override
			public void mouseDragged(MouseEvent e) {
			}

			@Override
			public void mouseReleased(MouseEvent e) {
			}

		};
		frame.addMouseListener(ma);
		frame.addMouseMotionListener(ma);

		BufferedImage buffer = new BufferedImage(worldSize, worldSize, BufferedImage.TYPE_INT_RGB);

		Graphics2D g2 = (Graphics2D) buffer.getGraphics();
		Graphics2D pg = (Graphics2D) panel.getGraphics();

		long last = System.nanoTime();
		long nsWaiting = 0;
		long frames = 0;
		while(true) {
			pm.sortNeighbors();
			pm.calculateForces();
			pm.applyForces();
			pm.render();
			byte[] out = pm.getOutputs();
//			System.out.println(Arrays.toString(out));

//			buffer.setRGB(0, 0, worldSize, worldSize, out, 0, worldSize);
			for(int x = 0; x < worldSize; x++) {
				for(int y = 0; y < worldSize; y++) {
					int id = x + y * worldSize;
					float d = (out[id] + 128) / 255f;
					d = Math.max(0, Math.min(1, d));

					int rgb = new Color(d, d, d).getRGB();

					buffer.setRGB(x, y, rgb);
				}
			}

			long now = System.nanoTime();
			nsWaiting = now - last;
			int fps = (int) (1e9 / nsWaiting);
			last = now;
			frames++;
			// System.out.println(nsWaiting / frames + "ns per frame");

			pg.drawImage(buffer, 0, 0, panel.getWidth(), panel.getHeight(), null);
			pg.setColor(Color.WHITE);
			pg.drawString("FPS: " + fps, 5, 35);
//			panel.getGraphics().fillRect(0, 0, 100, 100);

			while(!runLater.isEmpty()) {
				runLater.poll().run();
			}
		}

	}

	public void loadPTX(String fileName) {
		cuModuleLoad(module, "/assets/kernels/" + fileName + ".ptx");
	}

	public void allocateAndPushData(float[] particles, int entries, int maxParticles, int worldWidth, int worldHeight) {
		this.worldWidth = worldWidth;
		this.worldHeight = worldHeight;
		this.entries = entries;
		this.maxParticles = maxParticles;

		this.deviceInputs = new CUdeviceptr();
		allocateArray(deviceInputs, particles);

		this.deviceNeighbors = new CUdeviceptr();
		JCudaDriver.cuMemAlloc(deviceNeighbors, maxParticles * 32 * Sizeof.INT);

		this.deviceWorldData = new CUdeviceptr();
		allocateArray(deviceWorldData, new int[] { maxParticles });

		this.deviceOutputs = new CUdeviceptr();
		JCudaDriver.cuMemAlloc(deviceOutputs, (worldWidth * worldHeight) * Sizeof.BYTE);

		this.calculationParams = Pointer.to(
				Pointer.to(new int[] { worldWidth }),
				Pointer.to(new int[] { worldHeight }),
				Pointer.to(this.deviceWorldData),
				Pointer.to(new int[] { maxParticles }),
				Pointer.to(this.deviceInputs),
				Pointer.to(new int[] { entries }),
				Pointer.to(deviceNeighbors));

		this.renderParams = Pointer.to(
				Pointer.to(new int[] { worldWidth }),
				Pointer.to(new int[] { worldHeight }),
				Pointer.to(this.deviceWorldData),
				Pointer.to(new int[] { maxParticles }),
				Pointer.to(this.deviceInputs),
				Pointer.to(new int[] { entries }),
				Pointer.to(deviceOutputs));
	}

	public byte[] getOutputs() {
		// Allocate host output memory and copy the device output
		// to the host.
		byte hostOutput[] = new byte[worldWidth * worldHeight];
		cuMemcpyDtoH(Pointer.to(hostOutput), this.deviceOutputs,
				hostOutput.length * Sizeof.BYTE);

		return hostOutput;
	}

	public void cleanup() {
		JCudaDriver.cuMemFreeHost(this.deviceInputs);
		JCudaDriver.cuMemFreeHost(this.deviceOutputs);
	}

	public void applyForces() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) (maxParticles) / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.applyForcesFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.calculationParams, null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	public void calculateForces() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) (maxParticles) / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.calculateForcesFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.calculationParams, null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	public void sortNeighbors() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) (maxParticles) / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.neighborFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.calculationParams, null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	public void render() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) (worldWidth * worldHeight) / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.renderFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.renderParams, null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	private int allocateArray(CUdeviceptr device, float[] array) {
		int a = JCudaDriver.cuMemAlloc(device, array.length * Sizeof.FLOAT);
		int b = cuMemcpyHtoD(device, Pointer.to(array),
				array.length * Sizeof.FLOAT);
		return a + b;
	}

	private int allocateArray(CUdeviceptr device, int[] array) {
		int a = JCudaDriver.cuMemAlloc(device, array.length * Sizeof.FLOAT);
		int b = cuMemcpyHtoD(device, Pointer.to(array),
				array.length * Sizeof.FLOAT);
		return a + b;
	}

}
