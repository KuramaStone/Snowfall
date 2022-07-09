package me.brook.selection.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.ScreenUtils;

import me.brook.selection.LibDisplay;
import me.brook.selection.LibDisplay.RenderingMode;

public class InfoScreen extends DisplayScreen {

	private Texture lastNetwork;

	public InfoScreen(LibDisplay display) {
		super(display);
	}

	@Override
	public void show() {
		super.show();
	}

	@Override
	public void render(float delta) {
		updateReferences();

		if(engine.getRenderingMode() == RenderingMode.NETWORK) {

			if(lastNetwork != null || engine.getWorld().getBestBrainImage() != null) {

				batch.setProjectionMatrix(staticCamera.combined);
				batch.setShader(null);
				batch.begin();
				batch.setColor(Color.WHITE);

				if(engine.getWorld().getBestBrainImage() != null)
					lastNetwork = engine.getWorld().getBestBrainImage();

				batch.draw(lastNetwork, 0, -display.getImageHeight(), display.getImageWidth(), display.getImageHeight());

				batch.end();
				// drawInfo();
			}
		}
		else {
			ScreenUtils.clear(0.1f, 0.1f, 0.1f, 1);

			if(engine.getWorld().getCurrentGraph() != null) {
				batch.setProjectionMatrix(staticCamera.combined);
				batch.setShader(null);
				batch.begin();
				batch.setColor(Color.WHITE);

				Texture texture = engine.getWorld().getCurrentGraph();

				batch.draw(texture, 0, -display.getImageHeight(), display.getImageWidth(), display.getImageHeight());

				batch.end();
				display.drawInfo(true);

			}

		}
	}

	@Override
	public void hide() {
		super.hide();
	}

}
