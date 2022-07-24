extern "C"

#include <math.h>
#include <stdio.h>
#include <iostream>
#include <string>
#include <curand.h>
#include <curand_kernel.h>

using namespace std;

#define NEARCOUNT 32

class DistanceData {
public:
	int key;
	float distance;
};

extern "C" __device__ int HSBtoRGB(float hue, float saturation,
		float brightness) {
	int r = 0, g = 0, b = 0;
	if (saturation == 0) {
		r = g = b = (int) (brightness * 255.0f + 0.5f);
	} else {
		float h = (hue - (float) floor(hue)) * 6.0f;
		float f = h - (float) floor(h);
		float p = brightness * (1.0f - saturation);
		float q = brightness * (1.0f - saturation * f);
		float t = brightness * (1.0f - (saturation * (1.0f - f)));
		switch ((int) h) {
		case 0:
			r = (int) (brightness * 255.0f + 0.5f);
			g = (int) (t * 255.0f + 0.5f);
			b = (int) (p * 255.0f + 0.5f);
			break;
		case 1:
			r = (int) (q * 255.0f + 0.5f);
			g = (int) (brightness * 255.0f + 0.5f);
			b = (int) (p * 255.0f + 0.5f);
			break;
		case 2:
			r = (int) (p * 255.0f + 0.5f);
			g = (int) (brightness * 255.0f + 0.5f);
			b = (int) (t * 255.0f + 0.5f);
			break;
		case 3:
			r = (int) (p * 255.0f + 0.5f);
			g = (int) (q * 255.0f + 0.5f);
			b = (int) (brightness * 255.0f + 0.5f);
			break;
		case 4:
			r = (int) (t * 255.0f + 0.5f);
			g = (int) (p * 255.0f + 0.5f);
			b = (int) (brightness * 255.0f + 0.5f);
			break;
		case 5:
			r = (int) (brightness * 255.0f + 0.5f);
			g = (int) (p * 255.0f + 0.5f);
			b = (int) (q * 255.0f + 0.5f);
			break;
		}
	}
	return 0xff000000 | (r << 16) | (g << 8) | (b << 0);
}

extern "C" __device__ float atan2f(float y, float x) {
	float a = 0;

	if (x > 0) {
		a = atan(y / x);
	} else if (x < 0 && y >= 0) {
		a = atan(y / x) + 3.14159265359;
	} else if (x < 0 && y < 0) {
		a = atan(y / x) - 3.14159265359;
	} else if (x == 0 && y > 0) {
		a = 3.14159265359 / 2;
	} else if (x == 0 && y < 0) {
		a = -3.14159265359 / 2;
	}

	return a;
}

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

extern "C" __device__ int IX(int x, int y, int width, int entries) {
	return (x + y * width) * entries;
}

extern "C" __device__ float lerp(float i, float j, float a) {
	return i + a * (j - i);
}

extern "C" __device__ float min2(float i, float j) {
	if (i > j)
		return j;

	return i;
}
extern "C" __device__ void exchange(DistanceData *a, int i, int j) {
	DistanceData t = a[i];
	a[i] = a[j];
	a[j] = t;
}

extern "C" __device__ void compare(DistanceData *a, int i, int j, bool dir) {
	if (dir == (a[i].distance > a[j].distance))
		exchange(a, i, j);
}

extern "C" __device__ void bitonicMerge(DistanceData *a, int lo, int n,
		bool dir) {
	if (n > 1) {
		int m = n / 2;
		for (int i = lo; i < lo + m; i++)
			compare(a, i, i + m, dir);
		bitonicMerge(a, lo, m, dir);
		bitonicMerge(a, lo + m, m, dir);
	}
}

extern "C" __device__ void bitonicSort(DistanceData *a, int lo, int n,
		bool dir) {
	if (n > lo) {
		int m = (n - lo) / 2;
		bitonicSort(a, lo, m, true);
		bitonicSort(a, lo + m, m, false);
		bitonicMerge(a, lo, n, dir);
	}
}

extern "C" __device__ void sortDistances(DistanceData *a, int start, int end) {
	bitonicSort(a, start, end, true);
}

extern "C" __device__ float2 rotate(float2 center, float2 loc, float theta) {
	double cs = cos(theta);
	double sn = sin(theta);

	double translated_x = loc.x - center.x;
	double translated_y = loc.y - center.y;

	double result_x = translated_x * cs - translated_y * sn;
	double result_y = translated_x * sn + translated_y * cs;

	result_x += center.x;
	result_y += center.y;

	return make_float2(result_x, result_y);
}

extern "C" __global__ void sortNeighbors(int worldWidth, int worldHeight,
		int *worldData, int maxParticles, float *inputs, int entries,
		int *neighbors) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < worldData[0]) {
		float x = inputs[threadID * entries + 0];
		float y = inputs[threadID * entries + 1];
		float vx = inputs[threadID * entries + 2];
		float vy = inputs[threadID * entries + 3];
		float radius = inputs[threadID * entries + 4];
		float density = inputs[threadID * entries + 5];
		float mass = (radius * radius * 3.14159265359) * density;

		// really bad algorithm for now
		float maxRange = radius + radius * 20;
		float longRange2 = maxRange * maxRange;

		int nearbyIndex = 0;

		for (int i = 0; i < worldData[0]; i++) {
			int nearID = i;

			float x2 = inputs[nearID * entries + 0];
			float y2 = inputs[nearID * entries + 1];
			float r2 = inputs[nearID * entries + 4];

			float distRaw = pow(x - x2, 2) + pow(y - y2, 2);
			if (distRaw <= longRange2) {
				neighbors[threadID * NEARCOUNT + nearbyIndex] = nearID;

				if (nearbyIndex++ == NEARCOUNT)
					break;
			}
		}

	}
}

extern "C" __device__ float isOOB(int width, int height, float x, float y,
		float radius) {
	if (x < radius)
		return 6.28 / 4 * 2;
	if (x >= width - radius)
		return 6.28 / 4 * 0;
	if (y < radius)
		return 6.28 / 4 * 3;
	if (y >= height - radius)
		return 6.28 / 4 * 1;
	return -1;
}

extern "C" __global__ void applyForces(int worldWidth, int worldHeight,
		int *worldData, int maxParticles, float *inputs, int entries,
		int *neighbors) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < worldData[0]) {
		float x = inputs[threadID * entries + 0];
		float y = inputs[threadID * entries + 1];
		if (x == -1)
			return;
		float vx = inputs[threadID * entries + 2];
		float vy = inputs[threadID * entries + 3];
		float radius = inputs[threadID * entries + 4];

		// add new forces
		float vx2 = vx + inputs[threadID * entries + 6];
		float vy2 = vy + inputs[threadID * entries + 7];
//		vy2 += .2; // gravity

		// check if  it would take them out of bounds
		float collAngle = isOOB(worldWidth, worldHeight, x + vx2, y + vy2,
				radius);
		if (collAngle != -1) { // collides

			// rotate vx2, vy2 by collAngle and set vx2 to 0
			float2 result = rotate(make_float2(0, 0), make_float2(vx2, vy2),
					-collAngle);
			result.x = 0;
			result = rotate(make_float2(0, 0), result, collAngle); // rotate back and get final result
			vx2 = result.x;
			vy2 = result.y;
			vx2 = 0;
			vy2 = 0;

			// vx2 and vy2 will both no longer collide with the wall
		}
		x += vx2;
		y += vy2;

		float vel = pow(vx2, 2) + pow(vy2, 2);
		float resististance = 1.0 / (1 + pow(vel * 4, 2)); // slow down more as speed increases

		inputs[threadID * entries + 0] = x;
		inputs[threadID * entries + 1] = y;
		inputs[threadID * entries + 2] = vx2 * resististance;
		inputs[threadID * entries + 3] = vy2 * resististance;
		inputs[threadID * entries + 6] = 0;
		inputs[threadID * entries + 7] = 0;

	}
}

extern "C" __global__ void calculateForces(int worldWidth, int worldHeight,
		int *worldData, int maxParticles, float *inputs, int entries,
		int *neighbors) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < worldData[0]) {
		float x = inputs[threadID * entries + 0];
		float y = inputs[threadID * entries + 1];
		if (x == -1)
			return;
		float vx = inputs[threadID * entries + 2];
		float vy = inputs[threadID * entries + 3];
		float radius = inputs[threadID * entries + 4];
		float density = inputs[threadID * entries + 5];
		float mass = (radius * radius * 3.14159265359) * density;

		// calculate forces to other particles

		for (int i = 0; i < NEARCOUNT; i++) {
			int nearID = neighbors[threadID * NEARCOUNT + i];

			if (nearID != -1) {
				float x2 = inputs[nearID * entries + 0];
				float y2 = inputs[nearID * entries + 1];
				float vx2 = inputs[nearID * entries + 2];
				float vy2 = inputs[nearID * entries + 3];
				float r2 = inputs[nearID * entries + 4];
				float d2 = inputs[nearID * entries + 5];

				float distRaw = pow(x - x2, 2) + pow(y - y2, 2);

				float shortRangeRepulsionMax = radius + radius * 4 - r2;
				float force = 0.1;

				// if distance less than shortRange, pull it. Otherwise, it must be long range for it to be here
				if (distRaw <= pow(shortRangeRepulsionMax, 2)) {
					force = force / (1 + distRaw);

				} else {
					force = -force * distRaw / 1000;
				}

				float theta = atan2f(y - y2, x - x2);
				// get relative forces
				float fX = cos(theta) * force;
				float fY = sin(theta) * force;

				inputs[nearID * entries + 6] -= fX; // force to add next frame
				inputs[nearID * entries + 7] -= fY; // force to add next frame

			}

		}

	}

}

extern "C" __global__ void render(int worldWidth, int worldHeight,
		int *worldData, int maxParticles, float *inputs, int entries,
		char *output) {

	int threadID = blockIdx.x * blockDim.x + threadIdx.x;
	if (threadID < worldWidth * worldHeight) {
		int x = fmod2(threadID, worldWidth);
		int y = threadID / worldWidth;

		// get distance to closest particle

		float heatSum = 0;

		for (int i = 0; i < worldData[0]; i++) {

			int id = i;

			if (id != -1) {
				float x2 = inputs[id * entries + 0];
				float y2 = inputs[id * entries + 1];
				float hue = inputs[id * entries + 8];

				float dist = pow(x - x2, 2) + pow(y - y2, 2);

				heatSum += 1 / (1 + dist);
			}
		}
		heatSum = clamp2(0.8, 1, heatSum);

		output[threadID] = (255 * heatSum) - 128;

	}
}

