package me.brook.selection.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.ScreenUtils;

import me.brook.selection.LibDisplay;

public class LoadingScreen extends DisplayScreen {

	public LoadingScreen(LibDisplay display) {
		super(display);
	}

	@Override
	public void show() {
		super.show();
	}

	@Override
	public void render(float delta) {
		// we don't super 
		display.createCameras();
		updateReferences();
		ScreenUtils.clear(0, 0, 0, 1);
		batch.begin();

		font.setColor(Color.WHITE);
		GlyphLayout glyph = new GlyphLayout();
		glyph.setText(font, "Loading...\n[" + engine.loadingStage + "]");

		font.draw(batch, glyph, display.getImageWidth() / 2 - glyph.width / 2, display.getImageHeight() / 2 - glyph.height / 2);

		batch.end();
		batch.flush();
	}

	@Override
	public void hide() {
		super.hide();
	}

}
