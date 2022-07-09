#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

uniform float hue;
uniform float id;
uniform int totalPos;
uniform float[50 * 6] positions;
uniform sampler2D u_texture;
uniform float width;
uniform float height;

uniform int segmentWidth;
uniform int segmentHeight;

//	Simplex 3D Noise
//	by Ian McEwan, Ashima Arts
//
vec4 permute(vec4 x) {
	return mod(((x * 34.0) + 1.0) * x, 289.0);
}
vec4 taylorInvSqrt(vec4 r) {
	return 1.79284291400159 - 0.85373472095314 * r;
}

float snoise(vec3 v) {
	const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
	const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

// First corner
	vec3 i = floor(v + dot(v, C.yyy));
	vec3 x0 = v - i + dot(i, C.xxx);

// Other corners
	vec3 g = step(x0.yzx, x0.xyz);
	vec3 l = 1.0 - g;
	vec3 i1 = min(g.xyz, l.zxy);
	vec3 i2 = max(g.xyz, l.zxy);

	//  x0 = x0 - 0. + 0.0 * C
	vec3 x1 = x0 - i1 + 1.0 * C.xxx;
	vec3 x2 = x0 - i2 + 2.0 * C.xxx;
	vec3 x3 = x0 - 1. + 3.0 * C.xxx;

// Permutations
	i = mod(i, 289.0);
	vec4 p = permute(
			permute(
					permute(i.z + vec4(0.0, i1.z, i2.z, 1.0)) + i.y
							+ vec4(0.0, i1.y, i2.y, 1.0)) + i.x
					+ vec4(0.0, i1.x, i2.x, 1.0));

// Gradients
// ( N*N points uniformly over a square, mapped onto an octahedron.)
	float n_ = 1.0 / 7.0; // N=7
	vec3 ns = n_ * D.wyz - D.xzx;

	vec4 j = p - 49.0 * floor(p * ns.z * ns.z);  //  mod(p,N*N)

	vec4 x_ = floor(j * ns.z);
	vec4 y_ = floor(j - 7.0 * x_);    // mod(j,N)

	vec4 x = x_ * ns.x + ns.yyyy;
	vec4 y = y_ * ns.x + ns.yyyy;
	vec4 h = 1.0 - abs(x) - abs(y);

	vec4 b0 = vec4(x.xy, y.xy);
	vec4 b1 = vec4(x.zw, y.zw);

	vec4 s0 = floor(b0) * 2.0 + 1.0;
	vec4 s1 = floor(b1) * 2.0 + 1.0;
	vec4 sh = -step(h, vec4(0.0));

	vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
	vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

	vec3 p0 = vec3(a0.xy, h.x);
	vec3 p1 = vec3(a0.zw, h.y);
	vec3 p2 = vec3(a1.xy, h.z);
	vec3 p3 = vec3(a1.zw, h.w);

//Normalise gradients
	vec4 norm = taylorInvSqrt(
			vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
	p0 *= norm.x;
	p1 *= norm.y;
	p2 *= norm.z;
	p3 *= norm.w;

// Mix final noise value
	vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)),
			0.0);
	m = m * m;
	return 42.0
			* dot(m * m,
					vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
}

float getSmoothNoiseAt(vec3 pos, int layers, float periodExpo,
		float persistance) {

	float total = 0;
	float frequency = 1;
	float amplitude = 1;
	float maxValue = 0; // Used for normalizing result to 0.0 - 1.0

	for (int i = 0; i < layers; i++) {
		total += snoise(pos * frequency) * amplitude;

		maxValue += amplitude;

		amplitude *= persistance;
		frequency *= periodExpo;
	}

	return ((total / maxValue) + 1) / 2;
}

float cosInterpolate(float y1, float y2, float fraction) {
	float mu2 = (1 - cos(fraction * 3.141)) / 2;

	return (y1 * (1 - mu2) + y2 * mu2);
}

vec4 interpolate(vec4 a, vec4 b, float fraction) {

	float DELTA_RED = b.r - a.r;
	float DELTA_GREEN = b.g - a.g;
	float DELTA_BLUE = b.b - a.b;
	float DELTA_ALPHA = b.a - a.a;

	float red = a.r + (DELTA_RED * fraction);
	float green = a.g + (DELTA_GREEN * fraction);
	float blue = a.b + (DELTA_BLUE * fraction);
	float alpha = a.a + (DELTA_ALPHA * fraction);

	return vec4(red, green, blue, alpha);
}

vec3 hsv2rgb(vec3 c) {
	vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
	vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
	return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rgb2hsv(vec3 c) {
	vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
	vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
	vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

	float d = q.x - min(q.w, q.y);
	float e = 1.0e-10;
	return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

void main() {

	float zoom = 2;

	v_texCoords -= vec2(0.5);
	v_texCoords *= zoom;
	v_texCoords += vec2(0.5);

	float waveNoise = 1
			+ 0.1
					* (getSmoothNoiseAt(
							vec3((v_texCoords * vec2(width, height)) * 0.03,
									id), 2, 2, 0.3) * 2 - 1);

	v_texCoords *= waveNoise;

	vec4 closestColor = vec4(0);
	vec4 secondColor = vec4(0);

	float closestDist = 10000000;
	float secondClosestDist = 10000000;
	float totalDist = 0;
	float totalHeat = 0;
	float distTotal = 0;

	vec4 colorArray[100];
	float heatArray[100];

	for (int i = 0; i < totalPos; i++) { // maximum of 100 segments to render, 400 floats total
		float x = (positions[i * 6 + 0] + 0.5) * width;
		float y = (positions[i * 6 + 1] + 0.5) * height;
		float heat = positions[i * 6 + 2] * waveNoise;
		float r = positions[i * 6 + 3];
		float g = positions[i * 6 + 4];
		float b = positions[i * 6 + 5];
		vec4 color = vec4(r, g, b, 1);

		float dist = pow(x - v_texCoords.x * width, 2)
				+ pow(y - v_texCoords.y * height, 2);
		dist = pow(dist, 0.5) / 16;
		if (dist < closestDist) {
			secondClosestDist = closestDist;
			secondColor = closestColor;

			closestDist = dist;
			closestColor = color;
		}
		totalHeat += heat / (1 + dist * dist);

		colorArray[i] = color;
		heatArray[i] = heat / (1 + dist * dist);
	}

	vec4 finalColor = vec4(0);
	for (int i = 0; i < totalPos; i++) {
		if (heatArray[i] == 0)
			break;
		finalColor += colorArray[i] * pow((heatArray[i]) / totalHeat, 3) / 2;
	}
	finalColor.xyz = rgb2hsv(finalColor.xyz);
	finalColor.z = 1;
	finalColor.xyz = hsv2rgb(finalColor.xyz);
	finalColor = closestColor;

	float alpha = 1; //;

	if (totalHeat < 0.5)
		alpha = 0;

	if (closestDist < 0.6)
		finalColor = vec4(0, 0, 0, 1);
	if (closestDist < 0.5)
		finalColor = vec4(hsv2rgb(vec3(hue, 1, 1)), 1);

	finalColor.a = alpha;

	gl_FragColor = finalColor;
//	gl_FragColor = vec4(vec3(waveNoise), 1);

}

