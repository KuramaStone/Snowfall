package me.brook.selection.screens;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;

import me.brook.selection.LibDisplay;
import me.brook.selection.entity.Agent;
import me.brook.selection.entity.Agent.EntityViewInfo;
import me.brook.selection.entity.Agent.NearbyEntry;
import me.brook.selection.entity.Corpse;
import me.brook.selection.entity.Entity;
import me.brook.selection.entity.body.Structure;
import me.brook.selection.tools.Vector2;

public class EntityScreen extends DisplayScreen {

	public EntityScreen(LibDisplay display) {
		super(display);
	}

	@Override
	public void show() {
		super.show();
	}

	@Override
	public void render(float delta) {
		super.render(delta);

		display.buildAgents();
		ScreenUtils.clear(0.1f, 0.1f, 0.1f, 1);
		renderEntities();
		display.drawInfo(true);
	}

	public void renderEntities() {
		Rectangle2D bounds = engine.getWorld().getBorders().getBounds2D();
		Vector2 bl = new Vector2(bounds.getCenterX() - bounds.getWidth() / 2, bounds.getCenterY() - bounds.getHeight() / 2);

		batch.setColor(Color.WHITE);
		batch.setProjectionMatrix(worldCamera.combined);
		batch.enableBlending();
		batch.begin();

		batch.setShader(worldShader);
		worldShader.setUniformf("sunValue", (float) engine.getWorld().getDaytimeLightFactor());
		worldShader.setUniformf("sunAngle", (float) engine.getWorld().getAngleOfSun());
		worldShader.setUniformf("time", engine.getWorld().getTime());
		worldShader.setUniformMatrix("u_projTrans", batch.getProjectionMatrix());
		worldShader.setUniformf("worldWidth", (float) engine.getWorld().getWorldWidth());
		worldShader.setUniformf("worldHeight", (float) engine.getWorld().getWorldHeight());

		if(display.lastShadowTexture != null) {
			display.lastShadowTexture.getTexture().bind(1);
			worldShader.setUniformi("shadowMap", 1);
		}

		if(engine.getWorld().getBorders().getWorldTexture() != null) {
			Texture tex = engine.getWorld().getBorders().getWorldTexture();
			tex.bind(2);
			worldShader.setUniformi("worldMap", 2);
			Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
		}
		//
		// // draw background
		batch.draw(display.berrySprite, (float) (bl.x), (float) (bl.y),
				(float) bounds.getWidth(), (float) bounds.getHeight());

		// if(display.lastShadowTexture != null) {
		// batch.setShader(null);
		// batch.draw(display.lastShadowTexture, (float) (bl.x), (float) (bl.y),
		// (float) bounds.getWidth(), (float) bounds.getHeight());
		// }

		batch.setShader(agentShader);
		// draw agents

		Map<String, List<Agent>> listBySprite = display.getUsedTextures();
		Map<String, Sprite> spriteByString = display.getTexturesByDna();

		// sorting by sprite is faster due to not needing to swap textures
		for(String dna : display.getUsedTextures().keySet()) {
			Sprite sprite = spriteByString.get(dna);

			for(Entity entity : listBySprite.get(dna)) {
				if(entity != null) {
					renderEntity(sprite, entity);
				}
			}

		}

		// render corpses
		List<Entity> toRender = new ArrayList<>(engine.getWorld().getEntities());
		batch.setShader(null);
		for(int i = 0; i < toRender.size(); i++) {
			Entity entity = toRender.get(i);
			if(entity != null) {
				if(entity instanceof Corpse) {
					renderEntity(display.berrySprite, entity);
					toRender.remove(entity);
					i--;
				}
			}
		}

		batch.end();

		Entity sel = engine.getWorld().getSelectedEntity();

		if(sel != null) {

			if(sel.getAttachedTo() != null) {
				shapes.setColor(Color.ORANGE);
				shapes.setProjectionMatrix(worldCamera.combined);
				shapes.begin(ShapeType.Line);

				Vector2 location = sel.getLocation();
				Vector2 l = sel.getAttachedTo().getLocation();
				shapes.line((float) l.x, (float) l.y, (float) location.x, (float) location.y);

				shapes.end();
			}
		}

		if(sel != null && sel.isAgent()) {
			Agent agent = (Agent) sel;

			Vector2 location = agent.getLocation();

			if(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
				shapes.setProjectionMatrix(worldCamera.combined);
				shapes.begin(ShapeType.Line);

				// double size = agent.getSize();
				// double toSurface = Math.abs(location.y - engine.getWorld().getBorders().getBounds().getMaxY()) + 50;
				// double toFloor = Math.abs(location.y - engine.getWorld().getBorders().getBounds().getMinY()) + 50;
				// Rectangle2D re = new Rectangle2D.Double(location.x - size, location.y - size - toFloor, size * 2,
				// toFloor + toSurface + size);
				// shapes.rect((float) re.getMinX(), (float) re.getMinY(), (float) re.getWidth(), (float) re.getHeight());
				float r = (float) (agent.getSightRange());
				shapes.setColor(Color.RED);
				shapes.rect((float) location.x - r, (float) location.y - r, r * 2, r * 2);

				shapes.setColor(Color.MAGENTA);
				for(Entity ent : agent.getAttachedEntities()) {
					if(ent == null)
						continue;

					Vector2 l = ent.getLocation();
					shapes.line((float) l.x, (float) l.y, (float) location.x, (float) location.y);
				}

				shapes.setColor(Color.RED);
				List<NearbyEntry> nearby = agent.getLastNearbyEntities();
				if(nearby != null)
					for(Entry<Entity, EntityViewInfo> near : nearby) {
						if(near == null)
							continue;

						Vector2 l = near.getKey().getLocation();
						shapes.line((float) l.x, (float) l.y, (float) location.x, (float) location.y);
					}

				// if(agent.getParent() != null && agent.getParent().isAlive()) {
				// Vector2 l = agent.getParent().getLocation();
				// shapes.line((float) l.x, (float) l.y, (float) location.x, (float) location.y);
				// }

				shapes.end();
			}
		}

		// draw spatial hash
		// display.drawSpatialHash();

	}

	public void renderEntity(Sprite sprite, Entity entity) {
		if(sprite == null)
			return;
		Vector2 location = entity.getLocation();

		// check if on screen
		Vector3 projected = worldCamera.project(new Vector3(location.x, location.y, 0));
		if(projected.x < -(2 * Agent.getSizeOfSegment()) || projected.y < -(2 * Agent.getSizeOfSegment()) ||
				projected.x > worldCamera.viewportWidth + (2 * Agent.getSizeOfSegment()) ||
				projected.y > worldCamera.viewportHeight + (2 * Agent.getSizeOfSegment()))
			return;

		if(entity.isAgent()) {
			Agent agent = (Agent) entity;
			if(!agent.isAlive())
				return;
			float alpha = 0;
			// third value, b, is used for whether or not it is focused

			if(agent.getTicksSinceLastHurt() < 20) {
				float flickerSpeed = 8;
				double m = Math.floor((engine.getWorld().getTime() + agent.getLocation().x) / flickerSpeed);

				float r = 0, b = 0, g = 0;
				// flicker between red and white when damaged
				if((int) (m % 2) == 0) {
					r = 1;
					b = 0;
					g = 0.3f;
					alpha = 1f;
				}
				else {
					r = 1;
					g = 1;
					b = 1;
					alpha = 1;
				}

				batch.setColor(new Color(r, b, g, alpha));
			}
			else
				batch.setColor(new Color(1, 1, 1, alpha));

		}
		else
			batch.setColor(Color.WHITE);

		//
		float x = location.x;
		float y = location.y;
		float w = 0;
		float h = 0;

		float ox = w / 2;
		float oy = h / 2;

		Agent agent = null;
		if(entity.isAgent()) {
			agent = (Agent) entity;

			float zoom = 2f;
			float sizePerSeg = Agent.getSizeOfSegment();
			Structure structure = agent.getStructure();
			w = (float) structure.getBounds().getWidth() * sizePerSeg * zoom;
			h = (float) structure.getBounds().getHeight() * sizePerSeg * zoom;

			ox = (structure.getCoreOffset().x + 0.5f) * sizePerSeg * zoom;
			oy = (structure.getCoreOffset().y + 0.5f) * sizePerSeg * zoom;

			ox = (ox - w / 2) * (1 / zoom) + w / 2;
			oy = (oy - h / 2) * (1 / zoom) + h / 2;

			x -= ox;
			y -= oy;

		}
		else {
			w = (float) entity.getSize();
			h = (float) entity.getSize();
			ox = w / 2;
			oy = h / 2;
			x -= ox;
			y -= oy;
		}

		float rotation = (float) Math.toDegrees(entity.getRelativeDirection());

		batch.draw(sprite, x, y, ox, oy, w,
				h, 1, 1, rotation);

		// batch.end();
		//
		// shapes.setProjectionMatrix(worldCamera.combined);
		// shapes.begin(ShapeType.Line);
		// shapes.line(x+ox, y+oy, x+ox + (float) Math.cos(entity.getRelativeDirection()) *50, y+oy + (float) Math.sin(entity.getRelativeDirection()) * 50);
		// shapes.end();
		// batch.begin();

	}

	@Override
	public void hide() {
		super.hide();
	}

}
