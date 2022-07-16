#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;

vec3 hsv2rgb(vec3 c) {
	vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
	vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
	return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

bool compare(vec4 a, vec4 b, float variance) {

	if (abs(a.x - b.x) > variance) {
		return false;
	}
	if (abs(a.y - b.y) > variance) {
		return false;
	}
	if (abs(a.z - b.z) > variance) {
		return false;
	}
	if (abs(a.a - b.a) > variance) {
		return false;
	}

	return true;
}

void main() {
	vec4 texture = texture2D(u_texture, v_texCoords);

	// fill the white segments with their color
	gl_FragColor = v_color.a == 0 ? texture : v_color;

	if(texture == vec4(0, 0, 1, 1))
		discard;

}

