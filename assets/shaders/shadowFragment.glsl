#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;

void main() {
	float alpha = 0.5;

	if (texture2D(u_texture, v_texCoords) == vec4(0, 0, 1, 1))
		discard;

	// fill the white segments with their color
	gl_FragColor += vec4(vec3(0, 0, 0), alpha);
	gl_FragColor = texture2D(u_texture, v_texCoords);

}

