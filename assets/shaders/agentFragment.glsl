#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

uniform int isAgent;
uniform float time;
uniform sampler2D u_texture;

void main() {
	vec4 texture = texture2D(u_texture, v_texCoords);

	// fill the white segments with their color
	gl_FragColor = v_color.a == 0 ? texture : v_color;

	if (texture == vec4(0, 0, 1, 1))
		discard;

}

