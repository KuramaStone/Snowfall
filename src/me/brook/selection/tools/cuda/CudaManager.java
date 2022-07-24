package me.brook.selection.tools.cuda;

import static jcuda.driver.JCudaDriver.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdeviceptr;
import jcuda.driver.CUfunction;
import jcuda.driver.CUlimit;
import jcuda.driver.CUmodule;
import jcuda.driver.JCudaDriver;
import me.brook.neat.network.GConnection;
import me.brook.neat.network.NeatNeuron;
import me.brook.selection.entity.Agent;
import me.brook.selection.entity.body.Structure;
import me.brook.selection.tools.Vector2;

public class CudaManager {

	private CUmodule module;
	private CUfunction tickFunction, neighborFunction;
	private CUdeviceptr deviceInputs, deviceNeighbors, deviceWorldData, deviceNetworks;

	private int totalElements;
	private int totalAgents;
	private int elementsPerAgentEntry;
	private Pointer tickParameters, neighborParameters;

	public CudaManager() {
		// Enable exceptions and omit all subsequent error checks
		JCudaDriver.setExceptionsEnabled(true);

		// Initialize the driver and create a context for the first device.
		cuInit(0);
		CUdevice device = new CUdevice();
		cuDeviceGet(device, 0);
		CUcontext context = new CUcontext();
		cuCtxCreate(context, 0, device);

		module = new CUmodule();
		loadPTX("JCudaKernel");

		neighborFunction = new CUfunction();
		cuModuleGetFunction(neighborFunction, module, "sortNeighbors");
		
		JCudaDriver.cuCtxSetLimit(CUlimit.CU_LIMIT_PRINTF_FIFO_SIZE, 4096);
		// // add the methods
		// tickFunction = new CUfunction();
		// cuModuleGetFunction(tickFunction, module, "tickAgents");
	}

	// public float[] calculateNearbyNeighbors() {
	// }

	public float[] createNetworkDataFor(List<Agent> agents) {
		int maxNeurons = 0;
		for(Agent a : agents)
			if(a.getBrain().getTotalNeurons().size() > maxNeurons)
				maxNeurons = a.getBrain().getTotalNeurons().size();

		int maxConnections = 5;
		int lengthPerNeuron = 3 + maxConnections * 3;
		int lengthPerNetwork = 3 + maxNeurons * lengthPerNeuron;

		float[] networks = new float[4 + agents.size() * lengthPerNetwork];
		Arrays.fill(networks, -1);
		networks[0] = maxNeurons;
		networks[1] = maxConnections;
//		networks[2] = Agent.totalInputs + 1;
//		networks[3] = Agent.outputs;

		Map<Integer, Integer> ids = new HashMap<>();
		int idIndex = 0;
		int neuronIndex = -1;

		for(int i = 0; i < agents.size(); i++) {
			Agent a = agents.get(i);

			for(int j = 0; j < a.getBrain().getLayersCount(); j++) {
				List<NeatNeuron> layer = a.getBrain().getLayers().get(j);
				for(int k = 0; k < layer.size(); k++) {
					NeatNeuron nn = layer.get(k);
					int neuronID = ids.getOrDefault(nn.getNeuronID(), -1);
					if(neuronID == 1) {
						neuronID = idIndex;
						ids.put(nn.getNeuronID(), idIndex++);
					}

					neuronIndex++;
					networks[4 + neuronIndex * (lengthPerNeuron) + 0] = neuronID; // neuron id
					networks[4 + neuronIndex * (lengthPerNeuron) + 1] = 0; // neuron output
					networks[4 + neuronIndex * (lengthPerNeuron) + 2] = (j == a.getBrain().getLayersCount() - 1) ? 1 : 0; // neuron type

					if(!ids.containsKey(nn.getNeuronID()))
						ids.put(nn.getNeuronID(), idIndex++);

					for(int l = 0; l < maxConnections; l++) {

						if(l >= nn.getInputConnections().size()) {
							networks[4 + neuronIndex * (lengthPerNeuron) + 3 + (l * 3) + 0] = -1; // weight
							networks[4 + neuronIndex * (lengthPerNeuron) + 3 + (l * 3) + 1] = -1; // from
							networks[4 + neuronIndex * (lengthPerNeuron) + 3 + (l * 3) + 2] = -1; // to
						}
						else {
							GConnection gconn = nn.getInputConnections().get(l);

							int fromNeuron = ids.getOrDefault(gconn.getFromNeuron(), -1);
							int toNeuron = ids.getOrDefault(gconn.getToNeuron(), -1);

							if(fromNeuron == -1) {
								fromNeuron = idIndex;
								ids.put(gconn.getFromNeuron(), idIndex);
								idIndex++;
							}
							if(toNeuron == -1) {
								toNeuron = idIndex;
								ids.put(gconn.getToNeuron(), idIndex);
								idIndex++;
							}

							networks[4 + neuronIndex * (lengthPerNeuron) + 3 + (l * 3) + 0] = (float) gconn.getWeight(); // weight
							networks[4 + neuronIndex * (lengthPerNeuron) + 3 + (l * 3) + 1] = fromNeuron; // from
							networks[4 + neuronIndex * (lengthPerNeuron) + 3 + (l * 3) + 2] = toNeuron; // to
						}
					}
				}
			}
		}

		return networks;

	}

	public float[][] gatherDataFor(List<Agent> agents) {

		int count = agents.size();
		int dataPerAgent = 23; // +1 for bias
		float[][] inputs = new float[count][dataPerAgent];

		// input is vector of all
		for(int i = 0; i < count; i++) {
			Agent a = agents.get(i);
			a.reset();
			a.buildBody();
			a.buildHitbox();
			a.setCudaID(i);

			inputs[i][0] = a.getLocation().x;
			inputs[i][1] = a.getLocation().y;
			inputs[i][2] = a.getVelocity().x;
			inputs[i][3] = a.getVelocity().y;
			inputs[i][4] = a.getHitbox().getWidth();
			inputs[i][5] = a.getHitbox().getHeight();
			inputs[i][6] = 0; // blocking light factor
			inputs[i][7] = 0; // blocking chems factor
			inputs[i][8] = a.getAge(); // age
			inputs[i][9] = 10; // energy
			inputs[i][10] = (float) a.getRelativeDirection(); // rotation
			inputs[i][11] = a.getSightRange(); // sight
			inputs[i][12] = a.isAlive() ? 1 : 0; // living
			inputs[i][13] = a.getHue(); // hue
			inputs[i][14] = (float) a.getCurrentMetabolism(); // metabolism
			inputs[i][15] = (float) a.getBaseMetabolism(); // metabolism
			inputs[i][16] = (float) a.getStructure().getStructure().size(); // metabolism
			inputs[i][17] = (float) a.getDensity(); // metabolism
			inputs[i][18] = (float) a.getStructure().getByType(Structure.PHOTO).size();
			inputs[i][19] = (float) a.getStructure().getByType(Structure.CHEMO).size();
			inputs[i][20] = (float) a.getStructure().getByType(Structure.HUNT).size();
			inputs[i][21] = (float) a.getDietGene();
			inputs[i][22] = (float) a.getMaturity();

		}

		return inputs;
	}

	public void loadPTX(String fileName) {

		// String string = JCudaSamplesUtils.preparePtxFile("assets/kernels/" + fileName + ".cu");

		// byte ptxData[] = null;
		// try {
		// ptxData = toZeroTerminatedStringByteArray(
		// CudaManager.class.getResourceAsStream(
		// "/assets/kernels/" + fileName + ".ptx"));
		// }
		// catch(IOException e) {
		// e.printStackTrace();
		// }
		// // Load the module data
		// int error = JCudaDriver.cuModuleLoadData(module, ptxData);
		//
		// if(error != 0) {
		// System.err.println("Failed to load ptx");
		// System.exit(1);
		// }

		// boolean debug = true;
		//
		// if(debug) {
		// File ptx = new File("assets/kernels/" + fileName + ".ptx");
		// File cu = new File("assets/kernels/" + fileName + ".cu");
		// if(ptx.exists()) {
		// if(lastModified(ptx) < lastModified(cu)) {
		// try {
		// System.out.println("Loading ptx...");
		// Process p = Runtime.getRuntime().exec("\"C:\\Users\\Brookie\\Documents\\Programming\\Eclipse\\workspace2021\\World Simulation\\assets\\kernels\\compile.bat\"");
		// int exit = p.waitFor();
		//
		// if(exit != 0) {
		// System.err.println("Couldnt compile");
		// System.exit(1);
		// }
		// else {
		// System.out.println("Compiled ptx");
		// }
		// }
		// catch(Exception e) {
		// e.printStackTrace();
		// }
		// }
		// }
		// }
		// File ptx = new File(JCudaSamplesUtils.preparePtxFile("assets/kernels/" + fileName + ".cu"));
		// // Load the ptx file.
		cuModuleLoad(module, "/assets/kernels/" + fileName + ".ptx");

	}

	private float lastModified(File f) {
		try {
			return ((BasicFileAttributes) Files.readAttributes(Paths.get(f.getAbsolutePath()),
					BasicFileAttributes.class)).lastModifiedTime().toMillis();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		return -1;
	}

	private static byte[] toZeroTerminatedStringByteArray(
			InputStream inputStream) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte buffer[] = new byte[8192];
		while(true) {
			int read = inputStream.read(buffer);
			if(read == -1) {
				break;
			}
			baos.write(buffer, 0, read);
		}
		baos.write(0);
		return baos.toByteArray();
	}

	private int networkLength, worldLength;

	private float[][] inputs;
	private float[] worldData, networks;
	private CUdeviceptr deviceLocations;

	public void allocateAndPushData(float[][] inputs, float[] worldData, float[] networks) {
		// convert 3d array to 1d array
		this.totalAgents = inputs.length;
		this.elementsPerAgentEntry = inputs[0].length;
		int elements = totalAgents * elementsPerAgentEntry;

		// start combined array with data to split it back
		float[] combined = new float[elements];

		int index = 0;
		for(int i = 0; i < inputs.length; i++) {
			for(int j = 0; j < inputs[i].length; j++) {
				combined[index++] = inputs[i][j];
			}
		}

		this.networkLength = networks.length;
		this.worldLength = worldData.length;

		// Allocate the device input data, and copy the
		// host input data to the device

		totalElements = elements;
		this.deviceInputs = new CUdeviceptr();
		int a = allocateArray(this.deviceInputs, combined);

		this.deviceWorldData = new CUdeviceptr();
		int b = allocateArray(this.deviceWorldData, worldData);

		this.deviceNetworks = new CUdeviceptr();
		int c = allocateArray(this.deviceNetworks, networks);

		this.deviceNeighbors = new CUdeviceptr();
		int d = allocateArray(this.deviceNeighbors, new float[totalAgents * totalAgents * Sizeof.FLOAT]);

		// Set up the kernel parameters: A pointer to an array
		// of pointers which point to the actual values.
		this.tickParameters = Pointer.to(
				Pointer.to(this.deviceInputs),
				Pointer.to(new int[] { totalAgents }),
				Pointer.to(new int[] { elementsPerAgentEntry }),
				Pointer.to(this.deviceNeighbors),
				Pointer.to(this.deviceWorldData),
				Pointer.to(this.deviceNetworks));

		this.neighborParameters = Pointer.to(
				Pointer.to(this.deviceInputs),
				Pointer.to(new int[] { totalAgents }),
				Pointer.to(new int[] { elementsPerAgentEntry }),
				Pointer.to(this.deviceNeighbors));
	}

	// public float[][] getNeighbors() {
	// // Allocate host output memory and copy the device output
	// // to the host.
	// float hostOutput[] = new float[totalElements];
	// cuMemcpyDtoH(Pointer.to(hostOutput), this.deviceNeighbors,
	// (this.totalAgents * this.totalAgents) * (Sizeof.FLOAT + Sizeof.INT));
	//
	// int index = 0;
	// float[][] output = new float[totalAgents][totalAgents];
	// for(int i = 0; i < output.length; i++) {
	// for(int j = 0; j < output[i].length; j++) {
	// output[i][j] = hostOutput[index++];
	// }
	// }
	//
	// return output;
	// }

	public float[][] getInputs() {
		// Allocate host output memory and copy the device output
		// to the host.
		float hostOutput[] = new float[totalAgents * elementsPerAgentEntry];
		cuMemcpyDtoH(Pointer.to(hostOutput), this.deviceInputs,
				totalAgents * elementsPerAgentEntry * Sizeof.FLOAT);

		int index = 0;
		float[][] output = new float[totalAgents][elementsPerAgentEntry];
		for(int i = 0; i < output.length; i++) {
			for(int j = 0; j < output[i].length; j++) {
				output[i][j] = hostOutput[i * output[i].length + j];
			}
		}

		return output;
	}

	private float[] getNetwork() {
		// Allocate host output memory and copy the device output
		// to the host.
		float hostOutput[] = new float[networkLength];
		cuMemcpyDtoH(Pointer.to(hostOutput), this.deviceNetworks,
				networkLength * Sizeof.FLOAT);

		return hostOutput;
	}

	public void cleanup() {
		JCudaDriver.cuMemFreeHost(this.deviceInputs);
		JCudaDriver.cuMemFreeHost(this.deviceNeighbors);
		JCudaDriver.cuMemFreeHost(this.deviceNetworks);
		JCudaDriver.cuMemFreeHost(this.deviceWorldData);
	}

	/*
	 * each layer of inputs must have same size [0][0].length MUST equal [0][1].length
	 */
	public void tickAgents() {

		int blockX = 1024;
		int gridX = (int) Math.ceil(((double) totalAgents / blockX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.tickFunction,
				gridX, 1, 1, // Grid dimension
				blockX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				this.tickParameters, null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	public boolean allocateNeigbors(List<Agent> list) {
		if(list.isEmpty())
			return false;
		
		this.totalAgents = list.size();

		float[] locations = new float[totalAgents * 2];
		int index = 0;
		for(Agent a : list) {
			a.setCudaID(index);
			Vector2 loc = a.getLocation();
			locations[index * 2 + 0] = loc.x;
			locations[index * 2 + 1] = loc.y;
			
			index++;
		}
		
		deviceNeighbors = new CUdeviceptr();
		allocateArray(deviceNeighbors, new float[25]);
		
		deviceLocations = new CUdeviceptr();
		allocateArray(deviceLocations, new float[10]);

		this.neighborParameters = Pointer.to(
				Pointer.to(new int[] { totalAgents }),
				Pointer.to(deviceNeighbors),
				Pointer.to(deviceLocations));
		
		return true;
	}

	public float[][] getNeighbors() {
		// Allocate host output memory and copy the device output
		// to the host.
		float hostOutput[] = new float[totalAgents * totalAgents];
		cuMemcpyDtoH(Pointer.to(hostOutput), this.deviceNeighbors,
				totalAgents * totalAgents * Sizeof.FLOAT);

		float[][] output = new float[totalAgents][totalAgents];
		for(int i = 0; i < totalAgents; i++) {
			for(int j = 0; j < totalAgents; j++) {
				output[i][j] = hostOutput[i * totalAgents + j];
			}
		}

		return output;
	}

	public void sortNeighbors() {
		int blockSizeX = 1024;
		int grid = (int) Math.ceil(((double) totalAgents / blockSizeX));

		// Call the kernel function.
		int error = cuLaunchKernel(this.neighborFunction,
				grid, 1, 1, // Grid dimension
				blockSizeX, 1, 1, // Block dimension
				0, null, // Shared memory size and stream
				Pointer.to(
						Pointer.to(new int[] { totalAgents }),
						Pointer.to(deviceNeighbors),
						Pointer.to(deviceLocations)), null // Kernel- and extra parameters
		);
		cuCtxSynchronize();
	}

	private int allocateArray(CUdeviceptr device, float[] array) {
		int a = JCudaDriver.cuMemAlloc(device, array.length * Sizeof.FLOAT);
		int b = cuMemcpyHtoD(device, Pointer.to(array),
				array.length * Sizeof.FLOAT);
		return a + b;
	}

	// public static void main(String[] args) throws Exception {
	// int agents = 3;
	// int dataPerAgent = 2000;
	//
	// Rectangle2D bounds = new Rectangle2D.Double(0, 0, 1, 1);
	// float[] worldData = new float[] {
	// (float) bounds.getMinX(), (float) bounds.getMinY(),
	// (float) bounds.getMaxX(), (float) bounds.getMaxX(),
	// (float) bounds.getWidth(), (float) bounds.getHeight()
	// };
	//
	// float[][] inputs = new float[agents][dataPerAgent];
	// for(int i = 0; i < agents; i++)
	// for(int j = 0; j < dataPerAgent; j++)
	// inputs[i][j] = i;
	//
	// NeatNetwork network = new NeatNetwork(new InnovationHistory(), new LinearFunction(), new LinearFunction(),
	// new Phenotype(),
	// false, AgentLife.totalInputs, AgentLife.outputs);
	// network.connectAllLayers();
	// network.randomizeWeights(new BoundedRandomizer(1));
	//
	// double[] list = new double[AgentLife.totalInputs];
	// Arrays.fill(list, 1);
	// network.setInputs(list);
	// network.calculate();
	//
	// List<Agent> agentList = new ArrayList<>();
	// for(int i = 0; i < 5000; i++) {
	// Agent agent = new AgentLife();
	// agent.setBrain(network.copy());
	// agent.setLocation(new Vector2());
	//
	// agentList.add(agent);
	// }
	//
	// CudaManager cuda = new CudaManager();
	// cuda.allocateAndPushData(cuda.gatherDataFor(agentList), worldData,
	// cuda.createNetworkDataFor(agentList));
	//
	// System.out.println("Starting...");
	// for(int i = 0; i < 1000000; i++) {
	// // cuda.sortNeighbors();
	// cuda.tickAgents();
	// }
	//
	// float[] agentData = cuda.getInputs()[0];
	// float[] neuralOuts = new float[AgentLife.outputs];
	// System.arraycopy(agentData, agentData.length - neuralOuts.length, neuralOuts, 0, neuralOuts.length);
	//
	// System.out.println("Done!");
	// // System.out.println(Arrays.deepToString(cuda.getInputs()));
	// // System.out.println(Arrays.toString(neuralOuts));
	// // System.err.println(Arrays.toString(networkOutput));
	//
	// cuda.cleanup();
	// }

}
