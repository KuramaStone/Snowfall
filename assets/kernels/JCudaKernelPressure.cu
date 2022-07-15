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

extern "C" __device__ float clamp2(float min, float max, float a) {
	if (a > max)
		return max;
	if (a < min)
		return min;
	return a;
}

extern "C" __device__ float fmod2(float x, float y) {
	return x - trunc(x / y) * y;
}

extern "C" __device__ float distance(float2 a, float2 b) {
	return pow(a.x - b.x, 2) + pow(a.y - b.y, 2);
}

extern "C" __global__ void addVelocity(int index, float x, float y,
		float *inputs) {
	inputs[index + 1] += x;
	inputs[index + 2] += y;
}

extern "C" __global__ void addDensity(int index, float amount, float *inputs) {
	inputs[index + 0] += amount;
}

extern "C" __global__ void update(int worldWidth, int worldHeight, int entries,
		float *inputs, float* outputs) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < (worldWidth * worldHeight)) {
		float myDensity = inputs[threadID * entries + 3]; // unaltered density
		// density of -1 indicates a wall
		if (myDensity == -1)
			return;

		// set last calculation's result as the new current last value
		inputs[threadID * entries + 3] = inputs[threadID * entries + 0];
		inputs[threadID * entries + 4] = inputs[threadID * entries + 1];
		inputs[threadID * entries + 5] = inputs[threadID * entries + 2];

		outputs[threadID] = clamp2(0, 1000000, inputs[threadID * entries + 0]);
	}
}

extern "C" __global__ void move(int worldWidth, int worldHeight, int entries,
		float *inputs) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < (worldWidth * worldHeight)) {
		float myDensity = inputs[threadID * entries + 3]; // unaltered density
		// density of -1 indicates a wall
		if (myDensity == -1)
			return;

		int x = (int) fmod2(threadID, worldWidth);
		int y = (int) (threadID / worldWidth);
		float vx = inputs[threadID * entries + 1];
		float vy = inputs[threadID * entries + 2];

		if (vx == 0 && vy == 0)
			return;

		// add density and velocity to (x+vx, y+vy)
		int x2 = x + vx;
		int y2 = y + vy;
		x2 = clamp2(0, worldWidth-1, x2);
		y2 = clamp2(0, worldHeight-1, y2);
		if (x2 == 0) {
			if (vx < 0)
				vx = 0;
		}
		if (x2 == worldWidth - 1) {
			if (vx > 0)
				vx = 0;
		}
		if (y2 == 0) {
			if (vy < 0)
				vy = 0;
		}
		if (y2 == worldHeight - 1) {
			if (vy > 0)
				vy = 0;
		}

		if(x2 == x && y2 == y)
			return;

		int id2 = (x2 + y2 * worldWidth) * entries;

		if (inputs[id2] == -1)
			return; // don't move into walls

		// my spot
		inputs[threadID * entries + 0] = myDensity / 2;

		// next spot
		inputs[id2 + 0] += myDensity / 2; // next density
		inputs[id2 + 1] += vx * 1; // next vx
		inputs[id2 + 2] += vy * 1; // next vy
	}
}

extern "C" __global__ void diffuse(int worldWidth, int worldHeight, int entries,
		float *inputs, float *output) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < (worldWidth * worldHeight)) {
		float myDensity = inputs[threadID * entries + 3]; // unaltered density
		// density of -1 indicates a wall
		if (myDensity == -1)
			return;

		int x = (int) fmod2(threadID, worldWidth);
		int y = (int) (threadID / worldWidth);

		float newDensity = myDensity;

		// diffuse. average nearby cells that aren't barriers
		float a = -1;
		if (x + 1 >= 0 && x + 1 < worldWidth) {
			a = inputs[(x + 1 + y * worldWidth) * entries + 3];
		}
		float b = -1;
		if (x - 1 >= 0 && x - 1 < worldWidth) {
			b = inputs[(x - 1 + y * worldWidth) * entries + 3];
		}
		float c = -1;
		if (y + 1 >= 0 && y + 1 < worldHeight) {
			c = inputs[(x + (y + 1) * worldWidth) * entries + 3];
		}
		float d = -1;
		if (y - 1 >= 0 && y - 1 < worldHeight) {
			d = inputs[(x + (y - 1) * worldWidth) * entries + 3];
		}

		int total = 5;
		if (a == -1) {
			total--;
			a = 0;
		}
		if (b == -1) {
			total--;
			b = 0;
		}
		if (c == -1) {
			total--;
			c = 0;
		}
		if (d == -1) {
			total--;
			d = 0;
		}

		newDensity = (a + b + c + d + myDensity) / total; // diffused amount

		inputs[threadID * entries + 0] = newDensity; // current density
	}

}

