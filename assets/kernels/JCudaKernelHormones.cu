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

extern "C" __device__ float fmod2(float x, float y) {
	return x - trunc(x / y) * y;
}

extern "C" __device__ float distance(float2 a, float2 b) {
	return pow(a.x - b.x, 2) + pow(a.y - b.y, 2);
}

extern "C" __global__ void calculate(float *inputs, int totalAgents,
		int entries, int hormones, int size, float *output) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < (size*size)) {
		float2 pixel = make_float2(fmod2(threadID, size), threadID / size);

		for (int i = 0; i < totalAgents; i++) {
			float x = inputs[i * entries + 0];
			float y = inputs[i * entries + 1];
			double dist = distance(pixel, make_float2(x, y));

			for (int j = 0; j < hormones; j++) {
				// get hormone level from distance
				double level = inputs[i * entries + 2 + j] / (1 + dist);

				output[threadID * hormones + j] += level;
			}

		}

	}

}

