extern "C"

#include <math.h>
#include <stdio.h>
#include <iostream>
#include <string>
#include <curand.h>
#include <curand_kernel.h>

using namespace std;

class DistanceData {
public:
	int key;
	float distance;
};

//extern "C" __device__ float fmod2(float x, float y) {
//	return x - trunc(x / y) * y;
//}
//
//extern "C" __device__ float rnd(int seed) {
//	int a = seed;
//	a = (a ^ 61) ^ (a >> 16);
//	a = a + (a << 3);
//	a = a ^ (a >> 4);
//	a = a * 0x27d4eb2d;
//	a = a ^ (a >> 15);
//
//	return (float) a / 2147483647;
//}
//
//extern "C" __device__ float getDaytimeLightFactor(float *worldData) {
//	return 1;
//}
//
//extern "C" __device__ float clamp(float min, float max, float f) {
//	if (f > max)
//		return max;
//	if (f < min)
//		return min;
//	return f;
//}
//
//extern "C" __device__ float getLightAt(float *worldData, float y) {
//	float minY = worldData[1];
//	float height = worldData[5];
//	float yValue = abs(y - minY) / (height);
//	yValue = clamp(0, 1, yValue);
//
//	float lightLevels = pow(yValue, 16);
//	lightLevels = clamp(0, 1, lightLevels);
//
//	return lightLevels * getDaytimeLightFactor(worldData);
//}
//
//extern "C" __device__ float getChemicalAt(float *worldData, float y) {
//	float maxY = worldData[3];
//	float height = worldData[5];
//	float yValue = abs(y - maxY) / (height);
//	yValue = clamp(0, 1, yValue);
//
//	float chemicalLevels = pow(yValue, 16);
//	chemicalLevels = clamp(0, 1, chemicalLevels);
//
//	return chemicalLevels;
//}
//
//extern "C" __device__ float relativeThetaAngleTo(float from, float to) {
//	// double angle = Math.atan2(to.y, to.x) - Math.atan2(from.y, from.x);
//	float angle = from - to;
//	float PI = 3.14159265359;
//
//	if (angle > PI)
//		angle -= 2 * PI;
//	else if (angle < -PI)
//		angle += 2 * PI;
//
//	return angle;
//}
//
//extern "C" __device__ float2 rotate(float2 center, float2 location,
//		float theta) {
//	float cs = cos(theta);
//	float sn = sin(theta);
//
//	float translated_x = location.x - center.x;
//	float translated_y = location.y - center.y;
//
//	float result_x = translated_x * cs - translated_y * sn;
//	float result_y = translated_x * sn + translated_y * cs;
//
//	result_x += center.x;
//	result_y += center.y;
//
//	return make_float2(result_x, result_y);
//}
//
//extern "C" __device__ float3 HSVtoRGB(float H, float S, float V) {
//	float s = S / 100;
//	float v = V / 100;
//	float C = s * v;
//	float X = C * (1 - abs(fmod2(H / 60.0, 2) - 1));
//	float m = v - C;
//	float r, g, b;
//	if (H >= 0 && H < 60) {
//		r = C, g = X, b = 0;
//	} else if (H >= 60 && H < 120) {
//		r = X, g = C, b = 0;
//	} else if (H >= 120 && H < 180) {
//		r = 0, g = C, b = X;
//	} else if (H >= 180 && H < 240) {
//		r = 0, g = X, b = C;
//	} else if (H >= 240 && H < 300) {
//		r = X, g = 0, b = C;
//	} else {
//		r = C, g = 0, b = X;
//	}
//
//	return make_float3(r + m, g + m, b + m);
//}
//
//extern "C" __device__ float relu(float value) {
//	if (value < 0)
//		return 0;
//	else
//		return value;
//}
//
//extern "C" __device__ void respawnAgent(int threadID, int startIndex,
//		float *inputs, float *networks, float *worldData) {
//	worldData[6]++; // decrease increment of living agents
//
//	// overwrite the agent at this index with our own info
//	float locX = inputs[startIndex + 0];
//	float locY = inputs[startIndex + 1];
//	float velX = inputs[startIndex + 2];
//	float velY = inputs[startIndex + 3];
//	float rwidth = inputs[startIndex + 4]; // width of hitbox after being rotated
//	float rheight = inputs[startIndex + 5]; // height of hitbox after being rotated
//	float lightBlocked = inputs[startIndex + 6]; // blocking light factor
//	float chemsBlocked = inputs[startIndex + 7]; // blocking chems factor
//	float age = inputs[startIndex + 8]; // age
//	float energy = inputs[startIndex + 9]; // energy
//	float rotation = inputs[startIndex + 10]; // rotation
//
//	// respawn random location
//	inputs[startIndex + 0] = rnd((int) (worldData[7] + threadID) + 1)
//			* worldData[4] + worldData[0];
//	inputs[startIndex + 1] = rnd((int) (worldData[7] + threadID) + 2)
//			* worldData[5] + worldData[1];
//
//	inputs[startIndex + 8] = 1; // age to 1
//	inputs[startIndex + 9] = 10; // energy
//	inputs[startIndex + 10] = rnd((int) (worldData[7] + threadID) + 3) * 3.14
//			* 2;
//
//	inputs[startIndex + 12] = 1;
//
//}
//
//extern "C" __device__ void calculateForAgent(int threadID, int startIndex,
//		int totalAgents, int lengthOfAgentData, float *inputs, float *neighbors,
//		float *worldData, float *networks) {
//
//	// get misc variables from array
//
//	float locX = inputs[startIndex + 0];
//	float locY = inputs[startIndex + 1];
//	float velX = inputs[startIndex + 2];
//	float velY = inputs[startIndex + 3];
//	float width = inputs[startIndex + 4]; // width of hitbox after being rotated
//	float height = inputs[startIndex + 5]; // height of hitbox after being rotated
//	float lightBlocked = inputs[startIndex + 6]; // blocking light factor
//	float chemsBlocked = inputs[startIndex + 7]; // blocking chems factor
//	float age = inputs[startIndex + 8]; // age
//	float energy = inputs[startIndex + 9]; // energy
//	float rotation = inputs[startIndex + 10]; // rotation
//	float sightRange = inputs[startIndex + 11]; // sight range
//	bool living = inputs[startIndex + 12] == 1;
//	float hue = inputs[startIndex + 13]; // hue
//	float currentMetabolism = inputs[startIndex + 14]; // metabolism
//	float baseMetabolism = inputs[startIndex + 15]; // base metabolism
//	int segmentCount = inputs[startIndex + 16];
//	float density = inputs[startIndex + 17];
//	int photoCells = inputs[startIndex + 18];
//	int chemoCells = inputs[startIndex + 19];
//	int huntingCells = inputs[startIndex + 20];
//	float dietGene = inputs[startIndex + 21];
//	float maturity = inputs[startIndex + 22];
//	int indexAfterData = 23;
//
//	float2 location = make_float2(locX, locY);
//	float2 velocity = make_float2(velX, velY);
//
//	/*
//	 *
//	 * Calculate inputs for neural network. They are stored at the end of all the other inputs, right before the outputs
//	 *
//	 */
//	int nearbyAgents = 0;
//	float closestDist = neighbors[threadID * (totalAgents * 2) + 1];
//	int key = -1;
//	for (int i = 0; i < totalAgents; i++) {
//		if (i == threadID)
//			continue; // don't include self
//		float d = neighbors[threadID * totalAgents + i];
//		if (d < closestDist) {
//			d = closestDist;
//			key = i * totalAgents;
//		}
//		if (d < sightRange)
//			nearbyAgents++;
//	}
//
//	int totalNeurons = networks[0];
//	int maxConnections = networks[1];
//	int inputNeurons = networks[2];
//	int outputNeurons = networks[3];
//
//	float *neuralInput = new float[inputNeurons];
//
//	int index = 0;
//	if (closestDist < sightRange && key != -1) {
//		float2 relClosest = make_float2(inputs[key + 0], inputs[key + 1]);
//		relClosest.x -= inputs[key + 0];
//		relClosest.y -= inputs[key + 1];
//		relClosest = rotate(make_float2(0, 0), relClosest, -rotation);
//
//		float3 color = HSVtoRGB(hue * 360, 100, 100);
//
//		neuralInput[0] = color.x;
//		neuralInput[1] = color.y;
//		neuralInput[2] = color.z;
//		neuralInput[3] = relClosest.x / sightRange;
//		neuralInput[4] = relClosest.y / sightRange;
//		neuralInput[5] = relativeThetaAngleTo(rotation, inputs[key + 10]); // show entity's direction relative to our own
//		neuralInput[6] = 1.0 / (1 + inputs[key + 9] / 10000.0); // agent energy
//		neuralInput[7] = inputs[key + 2] / sightRange; // velocityX / sightrange
//		neuralInput[8] = inputs[key + 3] / sightRange; // velocityY / sightrange
//	} else {
//		neuralInput[0] = 0; // r
//		neuralInput[1] = 0; // g
//		neuralInput[2] = 0; // b
//		neuralInput[3] = 0;
//		neuralInput[4] = 0;
//		neuralInput[5] = 0;
//		neuralInput[6] = 1;
//		neuralInput[7] = 1;
//		neuralInput[8] = 1;
//	}
//
//	neuralInput[9] = getLightAt(worldData, locY);
//	neuralInput[10] = getChemicalAt(worldData, locY);
//	neuralInput[11] = velocity.x;
//	neuralInput[12] = velocity.y;
//	neuralInput[13] = 1.0 / (1 + age);
//	neuralInput[14] = 1.0 / (1 + energy);
//	neuralInput[15] = 1.0 / (1 + nearbyAgents);
//	neuralInput[16] = relativeThetaAngleTo(3.14159265359 / 2, rotation); // to light is just their direction relative to up
//	neuralInput[17] = 0; //wall == null ? 1 : distToWall / (sightRange * sightRange);// world.getFoodNoise(getLocation().x, getLocation().y);
//	neuralInput[18] = 0; // direction of current at current spot
//	neuralInput[19] = 0; // direction of sun
//	neuralInput[20] = 1; // bias
//
//	/// [neuronID,output,function, connWeight,connFrom,connTo, connWeight,connFrom,connTo...]
//	// [0,1,0, .5,0,2, -1,-1,-1]
//
//	int lengthPerNeuron = 3 + maxConnections * 3;
//	int lengthPerNetwork = totalNeurons * lengthPerNeuron;
//
//	// calculate neural network
//	int networkBegin = 4 + threadID * lengthPerNetwork;
//
//	// put network inputs into input array
//	for (int i = 0; i < networks[2]; i++) {
////		inputs[startIndex + indexAfterData + i] = neuralInput[i];
//		networks[networkBegin + (i * lengthPerNeuron) + 1] = neuralInput[i];
//	}
//
//	for (int i = 0; i < totalNeurons; i++) {
//		int neuronID = networks[networkBegin + i * lengthPerNeuron + 0];
//		int neuronFunction = networks[networkBegin + i * lengthPerNeuron + 3];
//
//		float sum = 0;
//		// get every input connection for this neuron
//		for (int j = 0; j < maxConnections; j++) {
//			int to = networks[networkBegin + i * lengthPerNeuron
//					+ (3 + j * 3 + 2)]; // the neuron the connection is from
//			int from = networks[networkBegin + i * lengthPerNeuron
//					+ (3 + j * 3 + 1)]; // the neuron the connection is from
//			if (to != -1 && from != -1) {
//				float weight = networks[networkBegin + i * lengthPerNeuron
//						+ (3 + j * 3 + 0)]; // weight of connection
//				float out = networks[networkBegin + from * lengthPerNetwork + 1]; // output of from neuron
//				sum += weight;
//			}
//		}
//
//		float output;
//		if (neuronFunction == 0)
//			output = relu(sum);
//		else if (neuronFunction == 1)
//			output = tanh(sum);
//
//		networks[networkBegin + i * lengthPerNeuron + 1] = sum;
//	}
//
//	// get outputs from neurons
//	int outputIndex = 0;
//	for (int i = totalNeurons - outputNeurons; i < totalNeurons; i++) {
//		float out = networks[networkBegin + i * lengthPerNeuron + 1]; // out
////		inputs[startIndex + indexAfterData + inputNeurons + outputIndex] = out;
//		outputIndex++;
//	}
//
//	float fullMass = 8 * segmentCount * density;
//	maturity = ((age * 0.004) / fullMass);
//	float matureMass = 8 * segmentCount * density * (maturity + 0.001);
//	currentMetabolism = (0.5 + baseMetabolism)
//			* (1 - clamp(0, 1, maturity - 1));
//
//	// use brain outputs
//
//	float rotDesire = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 0) * lengthPerNeuron + 1];
//	float movementForce = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 1) * lengthPerNeuron + 1];
//
//	bool wantsToReproduce = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 2) * lengthPerNeuron + 1] >= 0.0;
//	bool wantsToDigest = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 3) * lengthPerNeuron + 1] >= 0;
//	bool wantsToEat = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 4) * lengthPerNeuron + 1] >= 0;
//	bool wantsToPhotosynthesize = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 5) * lengthPerNeuron + 1] >= 0;
//	bool wantsToChemosynthesize = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 6) * lengthPerNeuron + 1] >= 0;
//	bool wantsToEjectFood = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 7) * lengthPerNeuron + 1];
//	bool wantsToHeal = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 8) * lengthPerNeuron + 1];
//	bool wantsToAttack = networks[networkBegin
//			+ (totalNeurons - outputNeurons + 9) * lengthPerNeuron + 1] >= 0;
//
//	// update variables with brain
//
//	rotation += rotDesire * (3.14 * 0.01);
//
//	velX = cos(rotation) * movementForce * fullMass;
//	velY = sin(rotation) * movementForce * fullMass;
//
//	// tick
//	age++;
//
//	locX += velX;
//	locY += velY;
//	velX *= 1.0 * (1 / (1 + (segmentCount * 4)));
//	velY *= 1.0 * (1 / (1 + (segmentCount * 4)));
//
//	locX = clamp(worldData[0], worldData[2], locX);
//	locY = clamp(worldData[1], worldData[3], locY);
//
//	float sizeUsage = pow(matureMass * currentMetabolism * 0.03, 1.25);
//	float movementUsage = (movementForce * matureMass) * 0.0000003 * 0;
//	float used = (sizeUsage + movementUsage);
//
//	energy -= used;
//
//	float energyPerUnit = 15;
//	float competition = clamp(0, 1,
//			((float) segmentCount - nearbyAgents) / segmentCount);
//	float lightGain = photoCells * energyPerUnit * getLightAt(worldData, locY)
//			* competition;
//	float chemGain = chemoCells * energyPerUnit * getChemicalAt(worldData, locY)
//			* competition;
//
//	energy += lightGain + chemGain;
//
//	if (energy <= 0) {
//		living = false;
//	}
//
//	if (maturity > 2.0) {
//		living = false;
//	}
//
//	// set inputs
//
//	inputs[startIndex + 0] = locX;
//	inputs[startIndex + 1] = locY;
//	inputs[startIndex + 2] = velX;
//	inputs[startIndex + 3] = velY;
//	inputs[startIndex + 8] = age; // age
//	inputs[startIndex + 9] = energy; // energy
//	inputs[startIndex + 10] = rotation; // rotation
//	inputs[startIndex + 12] = living ? 1 : 0;
//	inputs[startIndex + 14] = currentMetabolism;
//	inputs[startIndex + 22] = maturity;
//
//	delete[] neuralInput;
//
//	if (!living) {
//		worldData[6]--; // decrease increment of living agents
//
//		// respawn if too low
//		respawnAgent(threadID, startIndex, inputs, networks, worldData);
//	}
//}
//
//extern "C" __global__ void tickAgents(float *inputs, int totalAgents,
//		int lengthOfAgentData, float *neighbors, float *worldData,
//		float *networks) {
//
//	int threadID = (blockIdx.x * blockDim.x + threadIdx.x);
//	if (threadID < totalAgents) {
//		int startIndex = threadID * lengthOfAgentData; // starting index for inputs
//		calculateForAgent(threadID, startIndex, totalAgents, lengthOfAgentData,
//				inputs, neighbors, worldData, networks);
//
//		worldData[7] += 1.0 / totalAgents;
//		return;
//	}
//
//}
//
//extern "C" __device__ void exchange(DistanceData *a, int i, int j) {
//	DistanceData t = a[i];
//	a[i] = a[j];
//	a[j] = t;
//}
//
//extern "C" __device__ void compare(DistanceData *a, int i, int j, bool dir) {
//	if (dir == (a[i].distance > a[j].distance))
//		exchange(a, i, j);
//}
//
//extern "C" __device__ void bitonicMerge(DistanceData *a, int lo, int n,
//		bool dir) {
//	if (n > 1) {
//		int m = n / 2;
//		for (int i = lo; i < lo + m; i++)
//			compare(a, i, i + m, dir);
//		bitonicMerge(a, lo, m, dir);
//		bitonicMerge(a, lo + m, m, dir);
//	}
//}
//
//extern "C" __device__ void bitonicSort(DistanceData *a, int lo, int n,
//		bool dir) {
//	if (n > lo) {
//		int m = (n - lo) / 2;
//		bitonicSort(a, lo, m, true);
//		bitonicSort(a, lo + m, m, false);
//		bitonicMerge(a, lo, n, dir);
//	}
//}
//
//extern "C" __device__ void sortDistances(DistanceData *a, int start, int end) {
//	bitonicSort(a, start, end, true);
//}

extern "C" __global__ void sortNeighbors(int totalAgents, float *neighbors,
		float *locations) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < totalAgents) {

		float array[10];
		for (int i = 0; i < 10; i++)
			array[i] = i;


		if (threadID == 0)
			printf("Length: %d %d %d\n", (sizeof(locations) / sizeof(float)), (sizeof(neighbors) / sizeof(float), (sizeof(array) / sizeof(float))));

	}

}

