#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

uniform float width;
uniform float height;
uniform int seed;

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

float fade(float t) {
	return 6 * (t * t * t * t * t) - 15 * t + 10 * (t * t * t);
}

float getFadedNoiseAt(vec3 pos, int layers, float periodExpo,
		float persistance) {

	float total = 0;
	float frequency = 1;
	float amplitude = 1;
	float maxValue = 0; // Used for normalizing result to 0.0 - 1.0

	for (int i = 0; i < layers; i++) {
		total += fade(snoise(pos * frequency)) * amplitude;

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
	fraction = cosInterpolate(0, 1, fraction);

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

float modulo(float a, float b) {
	return a - (b * floor(a / b));
}

vec2 rotate(vec2 center, vec2 toRotate, float theta) {
	float cx = cos(theta);
	float sx = sin(theta);
	float x = center.x + (toRotate.x - center.x) * cx
			- (toRotate.y - center.y) * sx;
	float y = center.y + (toRotate.x - center.x) * sx
			- (toRotate.y - center.y) * cx;
	return vec2(x, y);
}

float rand(vec2 co) {
	return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

float mysign(float f) {
	if (f > 0)
		return 1;
	else if (f < 0)
		return -1;

	return 0;
}

float getCurveValueAt(vec2 p1, vec2 p2, float value) {
	float y = 0;

	if (p1.x > p2.x) {
		vec2 temp = p1;
		p1 = p2;
		p2 = temp;
	}

	if (value < p1.x) {
		y = cosInterpolate(0, p1.y, value / p1.x); // interpolate between 0 and p1.y
	} else if (value > p1.x && value < p2.x) {
		y = cosInterpolate(p1.y, p2.y, (value - p1.x) / (p2.x - p1.x)); // interpolate between p1.y and p2.y
	} else { // interpolate between p2.y and 1
		y = cosInterpolate(p2.y, 1, (value - p2.x) / (1 - p2.x));
	}

	return y;
}

void main() {
	float scale = (1.0 / 1800);
//	Noise seafloorMap = new Noise(6, (1.0 / 1800) * 3, 0.8, 3, 1234, 4214);
//	Noise cliffMap = new Noise(3, (1.0 / 1800) * 6, 0.25, 2, 1234, 4214);
//	Noise caveMap = new Noise(3, (1.0 / 1800) * 10, 0.25, 2, 1234, 4214);
//	Noise wormMap = new Noise(3, (1.0 / 1800) * 50, 0.5, 2, 1234, 4214);
//	Noise distortionMap = new Noise(3, (1.0 / 1800) * 25, 0.5, 2, 1234, 4214);

	vec2 p1 = vec2(0.6, 0.00);
	vec2 p2 = vec2(1, 1);

	float x = v_texCoords.x * width;
	float y = v_texCoords.y * height;
	float distortMap = getSmoothNoiseAt(vec3(x, y, seed + 6325) * scale * 25, 3,
			0.25, 2) * 2 - 1;
	x += distortMap * 50;

	float x1 = x
			+ (10
					* getSmoothNoiseAt(vec3(x, y, seed + 2154) * scale * 25, 3,
							0.5, 2));

	float seabedTurb = getSmoothNoiseAt(vec3(x, 1, seed + 5236) * scale * 3, 3,
			0.8, 2) * 2 - 1;
	float coralTurb = getSmoothNoiseAt(vec3(x, y, seed + 85436) * scale * 6, 2,
			0.25, 2) * 2 - 1;
	float cliffTurb = getSmoothNoiseAt(vec3(x, 1, seed + 326) * scale * 5, 2,
			0.25, 2);
	float caveNoise = getSmoothNoiseAt(vec3(x, y, seed + 6325) * scale * 20, 2,
			0.5, 2) * 2 - 1;

	float floorStart = 200;

	floorStart = floorStart + seabedTurb * 100;
	floorStart = floorStart
			+ getCurveValueAt(p1, p2, abs(cliffTurb)) * height * 12;

	vec4 color = vec4(0, 0, 1, 1); // water

	if (y <= floorStart) {
		color = vec4(1, 0.95, 0.7, 0); // impassable sand

		if (caveNoise < -0.5) {
			color = vec4(1, 0.83, .58, 1); // passable caves
		}

	}

	gl_FragColor = color;

}
