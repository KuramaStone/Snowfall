package me.brook.selection.tools;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;

import me.brook.selection.Engine;
import me.brook.selection.LibDisplay.RenderingMode;
import me.brook.selection.entity.Agent;
import me.brook.selection.entity.Entity;

public class InputDetector implements ApplicationListener, InputProcessor, Lwjgl3WindowListener {

	private Engine engine;

	private Vector2 rightMouseDown;
	private Vector2 originalDisplayCenter;
	private Random random;

	private boolean[] pressed = new boolean[2000];

	public InputDetector(Engine engine) {
		this.engine = engine;
		random = new Random();
	}

	@Override
	public boolean keyDown(int keycode) {
		pressed[keycode] = true;
		if(keycode == Input.Keys.F) {

			RenderingMode mode = engine.getRenderingMode();

			if(mode == RenderingMode.ENTITIES) {
				mode = RenderingMode.NETWORK;
			}
			else if(mode == RenderingMode.NETWORK) {
				mode = RenderingMode.GRAPH;
			}
			else if(mode == RenderingMode.GRAPH) {
				mode = RenderingMode.FAST;
			}
			else if(mode == RenderingMode.FAST) {
				mode = RenderingMode.ENTITIES;
			}
			engine.setRenderingMode(mode);

		}
		if(keycode == Input.Keys.R) {
			List<Agent> list = engine.getWorld().getSelectedSpecies();
			list.removeIf(a -> a == null || !a.isAlive());
			if(!list.isEmpty())
				engine.getWorld().setSelectedEntity(list.get(random.nextInt(list.size())));
		}
		if(keycode == Input.Keys.SPACE) {
			engine.getDisplay().setCenter(new Vector2());
			engine.setZoomValue(2.2);
			engine.getWorld().setSelectedEntity(null);
		}

		if(keycode == Input.Keys.ESCAPE) {
			engine.shutdown();
		}

		if(keycode == Input.Keys.LEFT) {
			engine.setFps(engine.getFps() - 5);
		}
		else if(keycode == Input.Keys.RIGHT) {
			engine.setFps(engine.getFps() + 5);
		}

		return false;
	}

	@Override
	public boolean keyUp(int keycode) {
		pressed[keycode] = false;
		return false;
	}

	@Override
	public boolean keyTyped(char character) {
		return false;
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		if(button == Input.Buttons.LEFT && pressed[Input.Keys.CONTROL_LEFT]) {
			Vector2 worldCoords = engine.getDisplay().transformScreenToWorld(new Vector2(screenX, screenY));

			setSelectedAgent(worldCoords);
		}
		else if(button == Input.Buttons.LEFT) {
			engine.getWorld().setCursorLocation(engine.getDisplay().transformScreenToWorld(new Vector2(screenX, screenY)));
		}

		if(button == Input.Buttons.RIGHT) {
			engine.getWorld().setSelectedEntity(null);

			rightMouseDown = new Vector2(screenX, screenY);
			originalDisplayCenter = engine.getDisplay().getCenter();
		}

		return false;
	}

	private void setSelectedAgent(Vector2 worldCoords) {
		float w = 200;
		float h = 200;
		List<Entity> nearby = Arrays.asList(engine.getWorld().getNearbyEntities(worldCoords, w));

		// get closest
		Entity closest = null;
		double range = Double.MAX_VALUE;
		for(Entity e : nearby) {
			if(e == null)
				continue;
			double d = e.getLocation().distanceToRaw(worldCoords);

			if((closest == null || d < range)) {
				range = d;
				closest = e;
			}
		}

		if(closest != null) {
			engine.getWorld().setSelectedEntity(closest);
		}
	}

	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		if(button == Input.Buttons.RIGHT) {
			rightMouseDown = null;
		}
		return false;
	}

	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		if(rightMouseDown != null) {
			Vector2 dragged = new Vector2(screenX, screenY).subtract(rightMouseDown);
			dragged = dragged.multiply(engine.getZoom());

			engine.getDisplay().setCenter(originalDisplayCenter.add(-dragged.x, dragged.y));

		}
		return false;
	}

	@Override
	public boolean mouseMoved(int screenX, int screenY) {

		return false;
	}

	@Override
	public boolean scrolled(float amountX, float amountY) {
		double scaleSpeed = 0.2;
		boolean up = amountY > 0;

		engine.setZoomValue(engine.getZoomValue() + (up ? scaleSpeed : -scaleSpeed));
		return false;
	}

	@Override
	public void create() {

	}

	@Override
	public void resize(int width, int height) {
	}

	@Override
	public void render() {

	}

	@Override
	public void pause() {

	}

	@Override
	public void resume() {

	}

	@Override
	public void dispose() {

	}

	@Override
	public void created(Lwjgl3Window window) {
		// TODO Auto-generated method stub

	}

	@Override
	public void iconified(boolean isIconified) {
		// TODO Auto-generated method stub

	}

	@Override
	public void maximized(boolean isMaximized) {
		// TODO Auto-generated method stub

	}

	@Override
	public void focusLost() {
		// TODO Auto-generated method stub

	}

	@Override
	public void focusGained() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean closeRequested() {
		engine.shutdown();
		System.exit(1);
		return false;
	}

	@Override
	public void filesDropped(String[] files) {
		// TODO Auto-generated method stub

	}

	@Override
	public void refreshRequested() {
		// TODO Auto-generated method stub

	}

}
