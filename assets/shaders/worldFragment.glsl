#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

uniform float sunAngle;
uniform float sunValue;
uniform float time;
uniform sampler2D u_texture;
uniform sampler2D shadowMap;
uniform sampler2D worldMap;
uniform float worldWidth;
uniform float worldHeight;

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

float lightValueAt(float y) {
	float yValue = max(0, min(1, y));
	float resistanceToTravel = 15;

	return 1.0 / (1 + yValue * resistanceToTravel);
}

float chemicalsAt(float y) {

	float yValue = y / 2 + 0.5; // scale it between [0.5-1.0]
	yValue = 1 - yValue; // flip it for chemo

	float resistanceToTravel = 15;

	float epochLength = 1000000;
	// chemical levels will rise and fall
	float chemicalEpoch = (cos(time * 3.14 / epochLength) + 1) / 2;

	return (1.0 / (1 + yValue * resistanceToTravel));
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

void main() {

	vec2 stretchedCoords = v_texCoords
			* vec2(worldWidth / 4000, worldHeight / 4000);

	float noiseScale = 1;
	float dispersionNoise = getSmoothNoiseAt(
			vec3(stretchedCoords * 2.5 * noiseScale, 1), 2, 3, 0.5);

	vec4 waterColor = vec4(
			vec3(0, 0.1, .5) * dispersionNoise * (1 - v_texCoords.y), 1);
	vec4 chemColor = vec4(vec3(.5, .02, .3), 1);
	vec4 lightColor = vec4(vec3(239, 249, 249) / 255, 1);

	// shade lightColor with sun shade

	float sunShimmer = (lightValueAt(v_texCoords.y)
			+ sin(v_texCoords.x + time / 500) / 40) * sunValue;

//	vec3 rotatedPosition = vec3(
//			rotate(vec2(0), v_texCoords.xy * 10, sunAngle + 3.14), time / 500);
//	rotatedPosition.y = 0;
	float lightBeamNoise = getSmoothNoiseAt(
			vec3(stretchedCoords.x * 10, 1, time / 500), 1, 3, 0.5);
	float lightBeam = pow(sunShimmer, 1.1) * lightBeamNoise;

	float chemNoise = getSmoothNoiseAt(vec3(stretchedCoords * 4, time / 200), 2, 2,
			0.8);
	chemNoise = 1 - (chemNoise * chemNoise);
	float chemAt = ((pow(v_texCoords.y, 3))) * chemNoise;

	vec2 shadowCoords = v_texCoords.xy;
	shadowCoords.y = 1 - shadowCoords.y;

	float entityShadows = max((1 - texture2D(shadowMap, shadowCoords).a), 0.25)
			* lightValueAt(v_texCoords.y);
	entityShadows = max(entityShadows, 0);
	entityShadows = min(entityShadows, 1);
	entityShadows = 0.5 + (entityShadows * 0.5);

	vec4 mix1 = waterColor; // water color
	vec4 mix2 = interpolate(mix1, lightColor, sunShimmer); // add sine wave sun shimmer
	vec4 mix3 = interpolate(mix2, lightColor, lightBeam); // add light beams
	vec4 mix4 = interpolate(vec4(0, 0, 0, 1), mix3, entityShadows); // add entity shadows
	vec4 mix5 = interpolate(mix4,
			interpolate(chemColor, vec4(1), pow(chemAt, 4)), chemAt); // add chemical fumes

	vec4 worldColor = texture2D(worldMap, v_texCoords);
	worldColor.a = 1;
	if(worldColor.rgb == vec3(0, 0, 1))
		gl_FragColor = mix5;
	else
		gl_FragColor = worldColor;

//	float x = v_texCoords.x * 8000;
//	float y = v_texCoords.y * 8000 * (9.0/16);
//	float scale = 1 / 1800.0;
//	gl_FragColor = vec4(vec3(getSmoothNoiseAt(vec3(x, y, 53264376) * scale * 30, 3,
//			0.25, 2)), 1);

}

