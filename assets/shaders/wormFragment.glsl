#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

uniform sampler2D u_texture;
uniform sampler2D wormTexture;

varying LOWP vec4 v_color;
varying vec2 v_texCoords;

void main() {

	vec2 flipped = v_texCoords;
	flipped.y = 1 - flipped.y;
	vec4 terrain = texture2D(u_texture, flipped);
	vec4 worms = texture2D(wormTexture, flipped);

	if (terrain == vec4(0, 0, 1, 1)) {
		gl_FragColor = terrain;
	} else {
		if (worms == vec4(1))
			gl_FragColor = v_color;
		else
			gl_FragColor = terrain;
	}

}
