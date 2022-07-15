package me.brook.selection.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.brook.selection.Engine;
import me.brook.selection.LibDisplay;

public class DisplayScreen extends ScreenAdapter {
	
	protected LibDisplay display;
	protected Engine engine;

	protected SpriteBatch batch;
	protected ShapeRenderer shapes;
	protected BitmapFont font;
	protected ShaderProgram worldShader, agentShader;
	
	protected OrthographicCamera worldCamera, staticCamera;
	
	public DisplayScreen(LibDisplay display) {
		this.display = display;
		this.engine = display.engine;
	}
	
	@Override
	public void render(float delta) {
		super.render(delta);
		
		display.createCameras();
		updateReferences();
		renderShadows();
	}
	
	public void renderShadows() {

		display.renderShadows();
	}

	protected void updateReferences() {
		batch = display.batch;
		shapes = display.shapes;
		font = display.font;
		worldShader = display.worldShader;
		agentShader = display.agentShader;
		worldCamera = display.worldCamera;
		staticCamera = display.staticCamera;
		
	}

}
