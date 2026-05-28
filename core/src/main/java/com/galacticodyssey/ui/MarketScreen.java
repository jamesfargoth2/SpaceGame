package com.galacticodyssey.ui;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.CargoChangedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.economy.events.TradeFailedEvent;
import com.galacticodyssey.economy.events.WalletChangedEvent;
import com.galacticodyssey.economy.service.TransactionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarketScreen implements Screen {

    private static final float WORLD_WIDTH  = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private static final Color TIER_UNCOMMON = new Color(0.2f, 0.85f, 0.2f, 1f);
    private static final Color TIER_RARE     = new Color(0.35f, 0.55f, 1f,  1f);
    private static final Color TIER_EXOTIC   = new Color(0.85f, 0.25f, 0.85f, 1f);
    private static final Color TIER_ALIEN    = new Color(1f, 0.65f, 0.1f, 1f);

    // ------------------------------------------------------------------
    // Row models
    // ------------------------------------------------------------------

    private static final class MarketRow {
        final String commodityId;
        final String name;
        final String tier;
        int stock;
        int price;

        MarketRow(String commodityId, String name, String tier, int stock, int price) {
            this.commodityId = commodityId;
            this.name = name;
            this.tier = tier;
            this.stock = stock;
            this.price = price;
        }
    }

    private static final class CargoRow {
        final String commodityId;
        final String name;
        int quantity;
        final int unitValue;

        CargoRow(String commodityId, String name, int quantity, int unitValue) {
            this.commodityId = commodityId;
            this.name = name;
            this.quantity = quantity;
            this.unitValue = unitValue;
        }
    }

    // ------------------------------------------------------------------
    // Component mappers
    // ------------------------------------------------------------------

    private static final ComponentMapper<MarketComponent>      MARKET_M  = ComponentMapper.getFor(MarketComponent.class);
    private static final ComponentMapper<PricingComponent>     PRICING_M = ComponentMapper.getFor(PricingComponent.class);
    private static final ComponentMapper<PlayerWalletComponent> WALLET_M  = ComponentMapper.getFor(PlayerWalletComponent.class);
    private static final ComponentMapper<CargoBayComponent>    CARGO_M   = ComponentMapper.getFor(CargoBayComponent.class);

    // ------------------------------------------------------------------
    // Dependencies
    // ------------------------------------------------------------------

    private final GalacticOdyssey     game;
    private final Screen              returnTo;
    private final Entity              station;
    private final Entity              player;
    private final Entity              ship;
    private final TransactionService  transactionService;
    private final CommodityRegistry   commodityRegistry;
    private final EventBus            eventBus;
    private final Skin                skin;
    private final AudioManager        audio;

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private final Stage               stage;
    private final StarfieldBackground starfield;
    private final OrthographicCamera  backgroundCamera;
    private       Texture             overlayTexture;

    // ------------------------------------------------------------------
    // Live UI labels updated by events
    // ------------------------------------------------------------------

    private Label creditsLabel;
    private Label cargoCapLabel;
    private Label statusLabel;
    private Label selectedLabel;
    private Label qtyLabel;

    // ------------------------------------------------------------------
    // List tables rebuilt on data changes
    // ------------------------------------------------------------------

    private Table stationTable;
    private Table cargoTable;

    // ------------------------------------------------------------------
    // Selection state
    // ------------------------------------------------------------------

    private int     quantity            = 1;
    private String  selectedCommodityId = null;
    private boolean isBuyMode           = true;

    private final List<MarketRow> marketRows = new ArrayList<>();
    private final List<CargoRow>  cargoRows  = new ArrayList<>();

    // ------------------------------------------------------------------
    // Event listeners (held for unsubscribe)
    // ------------------------------------------------------------------

    private final EventBus.EventListener<TradeCompletedEvent> onTradeCompleted;
    private final EventBus.EventListener<TradeFailedEvent>    onTradeFailed;
    private final EventBus.EventListener<WalletChangedEvent>  onWalletChanged;
    private final EventBus.EventListener<CargoChangedEvent>   onCargoChanged;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public MarketScreen(GalacticOdyssey game, Screen returnTo,
                        Entity station, Entity player, Entity ship,
                        TransactionService transactionService,
                        CommodityRegistry commodityRegistry,
                        EventBus eventBus) {
        this.game               = game;
        this.returnTo           = returnTo;
        this.station            = station;
        this.player             = player;
        this.ship               = ship;
        this.transactionService = transactionService;
        this.commodityRegistry  = commodityRegistry;
        this.eventBus           = eventBus;
        this.skin               = game.getSkin();
        this.audio              = game.getAudioManager();

        backgroundCamera = new OrthographicCamera();
        starfield        = new StarfieldBackground(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        stage            = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0.72f);
        pm.fill();
        overlayTexture = new Texture(pm);
        pm.dispose();

        onTradeCompleted = this::handleTradeCompleted;
        onTradeFailed    = this::handleTradeFailed;
        onWalletChanged  = this::handleWalletChanged;
        onCargoChanged   = this::handleCargoChanged;

        eventBus.subscribe(TradeCompletedEvent.class, onTradeCompleted);
        eventBus.subscribe(TradeFailedEvent.class,    onTradeFailed);
        eventBus.subscribe(WalletChangedEvent.class,  onWalletChanged);
        eventBus.subscribe(CargoChangedEvent.class,   onCargoChanged);

        refreshMarketData();
        buildUi();
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void refreshMarketData() {
        MarketComponent  market  = MARKET_M.get(station);
        PricingComponent pricing = PRICING_M.get(station);

        marketRows.clear();
        for (Map.Entry<String, MarketEntry> e : market.entries.entrySet()) {
            String          id    = e.getKey();
            MarketEntry     entry = e.getValue();
            CommodityDefinition def = commodityRegistry.get(id);
            if (def == null) continue;
            int price = pricing.prices.getOrDefault(id, def.basePrice);
            marketRows.add(new MarketRow(id, def.name, def.tier.name(), entry.stock, price));
        }
        marketRows.sort((a, b) -> a.name.compareTo(b.name));
        refreshCargoData();
    }

    private void refreshCargoData() {
        MarketComponent  market  = MARKET_M.get(station);
        PricingComponent pricing = PRICING_M.get(station);
        CargoBayComponent cargo  = CARGO_M.get(ship);

        cargoRows.clear();
        for (Map.Entry<String, Integer> e : cargo.contents.entrySet()) {
            String id  = e.getKey();
            int    qty = e.getValue();
            if (!market.entries.containsKey(id)) continue; // station doesn't buy this
            CommodityDefinition def = commodityRegistry.get(id);
            if (def == null) continue;
            int price = pricing.prices.getOrDefault(id, def.basePrice);
            cargoRows.add(new CargoRow(id, def.name, qty, price));
        }
        cargoRows.sort((a, b) -> a.name.compareTo(b.name));
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private void buildUi() {
        MarketComponent   market = MARKET_M.get(station);
        PlayerWalletComponent wallet = WALLET_M.get(player);
        CargoBayComponent cargo  = CARGO_M.get(ship);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(16);

        // Header row
        Table header = new Table();
        String stationName = market.stationId != null ? formatId(market.stationId) : "Station";
        header.add(new Label("MARKET — " + stationName, skin, "title")).left().expandX().fillX();
        creditsLabel = new Label(fmtCredits(wallet.credits), skin, "header");
        header.add(creditsLabel).right().padLeft(20);
        root.add(header).expandX().fillX().padBottom(6).row();

        // Sub-header: cargo capacity
        cargoCapLabel = new Label(fmtCargo(cargo), skin, "body");
        root.add(cargoCapLabel).left().padBottom(10).row();

        // Two-panel section
        Table panels = new Table();

        // Left: station inventory
        Table stationPanel = new Table();
        stationPanel.top().left();
        stationPanel.add(new Label("STATION INVENTORY", skin, "header")).left().padBottom(6).colspan(3).row();
        addColumnHeaders(stationPanel,
            new String[]{"COMMODITY", "IN STOCK", "PRICE/U"},
            new float[]{280f, 80f, 100f});
        stationTable = new Table();
        stationTable.top().left();
        buildStationRows();
        ScrollPane stationScroll = new ScrollPane(stationTable, skin);
        stationScroll.setFadeScrollBars(false);
        stationPanel.add(stationScroll).colspan(3).expandX().fillX().expandY().fillY();

        // Right: player cargo
        Table cargoPanel = new Table();
        cargoPanel.top().left();
        cargoPanel.add(new Label("YOUR CARGO", skin, "header")).left().padBottom(6).colspan(3).row();
        addColumnHeaders(cargoPanel,
            new String[]{"COMMODITY", "QTY", "SELL FOR"},
            new float[]{220f, 60f, 100f});
        cargoTable = new Table();
        cargoTable.top().left();
        buildCargoRows();
        ScrollPane cargoScroll = new ScrollPane(cargoTable, skin);
        cargoScroll.setFadeScrollBars(false);
        cargoPanel.add(cargoScroll).colspan(3).expandX().fillX().expandY().fillY();

        panels.add(stationPanel).expandX().fillX().expandY().fillY().padRight(16);
        panels.add(cargoPanel).expandX().fillX().expandY().fillY();

        root.add(panels).expand().fill().row();

        // Action bar
        root.add(buildActionBar()).expandX().fillX().padTop(8).row();

        // Status line
        statusLabel = new Label("", skin, "body");
        root.add(statusLabel).left().padTop(2);

        stage.addActor(root);
    }

    private void addColumnHeaders(Table parent, String[] names, float[] widths) {
        Label.LabelStyle style = skin.get("slot-detail", Label.LabelStyle.class);
        for (int i = 0; i < names.length; i++) {
            Label lbl = new Label(names[i], style);
            parent.add(lbl).width(widths[i]).left().padBottom(3);
        }
        parent.row();
    }

    private Table buildActionBar() {
        Table bar = new Table();

        selectedLabel = new Label("Select an item to trade", skin, "body");
        bar.add(selectedLabel).expandX().left().padRight(16);

        // Qty controls
        TextButton minusBtn = new TextButton("-", skin, "small");
        minusBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (quantity > 1) { quantity--; refreshActionBar(); }
            }
        });

        qtyLabel = new Label("1", skin, "header");
        qtyLabel.setAlignment(com.badlogic.gdx.utils.Align.center);

        TextButton plusBtn = new TextButton("+", skin, "small");
        plusBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                quantity++;
                clampQuantity();
                refreshActionBar();
            }
        });

        TextButton maxBtn = new TextButton("MAX", skin, "small");
        maxBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                quantity = calcMax();
                refreshActionBar();
            }
        });

        bar.add(minusBtn).size(36).padRight(4);
        bar.add(qtyLabel).width(48).height(36).padRight(4);
        bar.add(plusBtn).size(36).padRight(8);
        bar.add(maxBtn).width(56).height(36).padRight(20);

        TextButton buyBtn = new TextButton("BUY", skin);
        buyBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (selectedCommodityId == null || !isBuyMode) return;
                audio.playSound("audio/sfx/ui_click.ogg");
                transactionService.buy(station, player, ship, selectedCommodityId, quantity);
            }
        });

        TextButton sellBtn = new TextButton("SELL", skin);
        sellBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (selectedCommodityId == null || isBuyMode) return;
                audio.playSound("audio/sfx/ui_click.ogg");
                transactionService.sell(station, player, ship, selectedCommodityId, quantity);
            }
        });

        TextButton closeBtn = new TextButton("CLOSE", skin, "small-red");
        closeBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                close();
            }
        });

        bar.add(buyBtn).width(110).height(44).padRight(8);
        bar.add(sellBtn).width(110).height(44).padRight(20);
        bar.add(closeBtn).width(100).height(44);

        return bar;
    }

    // ------------------------------------------------------------------
    // Row builders (rebuilt after each transaction)
    // ------------------------------------------------------------------

    private void buildStationRows() {
        stationTable.clear();
        if (marketRows.isEmpty()) {
            stationTable.add(new Label("No commodities available.", skin, "body")).left().padTop(8);
            return;
        }
        for (MarketRow row : marketRows) {
            Color nameColor = tierColor(row.tier);

            Label nameLabel  = new Label(row.name, skin, "body");
            nameLabel.setColor(nameColor);
            Label stockLabel = new Label(row.stock > 0 ? String.valueOf(row.stock) : "—", skin, "body");
            if (row.stock <= 0) stockLabel.setColor(Color.DARK_GRAY);
            Label priceLabel = new Label(fmt("%,d cr", row.price), skin, "body");

            Table rowTable = new Table();
            rowTable.add(nameLabel).width(280f).left().padBottom(2);
            rowTable.add(stockLabel).width(80f).center().padBottom(2);
            rowTable.add(priceLabel).width(100f).right().padBottom(2);

            if (row.stock > 0) {
                rowTable.addListener(new ClickListener() {
                    @Override public void clicked(InputEvent event, float x, float y) {
                        selectedCommodityId = row.commodityId;
                        isBuyMode           = true;
                        quantity            = 1;
                        refreshActionBar();
                        setStatus("");
                    }
                });
            }

            stationTable.add(rowTable).expandX().fillX().padBottom(1).row();
        }
    }

    private void buildCargoRows() {
        cargoTable.clear();
        if (cargoRows.isEmpty()) {
            cargoTable.add(new Label("Cargo hold empty.", skin, "body")).left().padTop(8);
            return;
        }
        for (CargoRow row : cargoRows) {
            Label nameLabel = new Label(row.name, skin, "body");
            Label qtyLbl    = new Label(String.valueOf(row.quantity), skin, "body");
            Label valLabel  = new Label(fmt("%,d cr", row.unitValue), skin, "body");
            valLabel.setColor(new Color(0.3f, 1f, 0.5f, 1f));

            Table rowTable = new Table();
            rowTable.add(nameLabel).width(220f).left().padBottom(2);
            rowTable.add(qtyLbl).width(60f).center().padBottom(2);
            rowTable.add(valLabel).width(100f).right().padBottom(2);

            rowTable.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    selectedCommodityId = row.commodityId;
                    isBuyMode           = false;
                    quantity            = 1;
                    refreshActionBar();
                    setStatus("");
                }
            });

            cargoTable.add(rowTable).expandX().fillX().padBottom(1).row();
        }
    }

    // ------------------------------------------------------------------
    // Event handlers (called on render thread via ClickListener chain)
    // ------------------------------------------------------------------

    private void handleTradeCompleted(TradeCompletedEvent e) {
        CommodityDefinition def  = commodityRegistry.get(e.commodityId);
        String              name = def != null ? def.name : e.commodityId;
        String              verb = e.isBuy ? "Bought" : "Sold";
        setStatus(fmt("%s %d × %s  —  %,d cr", verb, e.quantity, name, e.totalPrice));
        refreshMarketData();
        buildStationRows();
        buildCargoRows();
        clampQuantity();
        refreshActionBar();
    }

    private void handleTradeFailed(TradeFailedEvent e) {
        switch (e.reason) {
            case INSUFFICIENT_FUNDS:      setStatus("Insufficient credits."); break;
            case CARGO_FULL:              setStatus("Cargo hold is full."); break;
            case INSUFFICIENT_STOCK:      setStatus("Station doesn't have enough stock."); break;
            case COMMODITY_NOT_IN_CARGO:  setStatus("You don't have that in your cargo."); break;
            case HOSTILE_FACTION:         setStatus("This faction refuses to trade with you."); break;
            default:                      setStatus("Trade failed.");
        }
    }

    private void handleWalletChanged(WalletChangedEvent e) {
        if (creditsLabel != null) {
            creditsLabel.setText(fmtCredits(e.newBalance));
        }
    }

    private void handleCargoChanged(CargoChangedEvent e) {
        CargoBayComponent cargo = CARGO_M.get(ship);
        if (cargoCapLabel != null) cargoCapLabel.setText(fmtCargo(cargo));
        refreshCargoData();
        if (cargoTable != null) buildCargoRows();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void refreshActionBar() {
        if (selectedLabel == null) return;
        if (selectedCommodityId == null) {
            selectedLabel.setText("Select an item to trade");
            qtyLabel.setText("1");
            return;
        }
        CommodityDefinition def  = commodityRegistry.get(selectedCommodityId);
        String              name = def != null ? def.name : selectedCommodityId;
        String              verb = isBuyMode ? "Buy" : "Sell";
        selectedLabel.setText(fmt("%s: %s", verb, name));
        qtyLabel.setText(String.valueOf(quantity));
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }

    private void clampQuantity() {
        int max = calcMax();
        if (quantity > max) quantity = max;
        if (quantity < 1)   quantity = 1;
    }

    private int calcMax() {
        if (selectedCommodityId == null) return 1;
        if (isBuyMode) {
            MarketComponent       market  = MARKET_M.get(station);
            PlayerWalletComponent wallet  = WALLET_M.get(player);
            PricingComponent      pricing = PRICING_M.get(station);
            CargoBayComponent     cargo   = CARGO_M.get(ship);
            MarketEntry entry = market.entries.get(selectedCommodityId);
            if (entry == null) return 1;
            int price = pricing.prices.getOrDefault(selectedCommodityId, 1);
            int canAfford   = price > 0 ? (int) (wallet.credits / price) : entry.stock;
            CommodityDefinition def = commodityRegistry.get(selectedCommodityId);
            int fitsInCargo = (def != null && def.volume > 0)
                ? (int) ((cargo.capacity - cargo.usedVolume) / def.volume)
                : entry.stock;
            return Math.max(1, Math.min(entry.stock, Math.min(canAfford, fitsInCargo)));
        } else {
            CargoBayComponent cargo = CARGO_M.get(ship);
            return Math.max(1, cargo.contents.getOrDefault(selectedCommodityId, 0));
        }
    }

    private void close() {
        game.setScreen(returnTo);
    }

    private static String fmtCredits(long credits) {
        return fmt("Credits: %,d", credits);
    }

    private static String fmtCargo(CargoBayComponent cargo) {
        return fmt("Cargo: %.0f / %.0f t", cargo.usedVolume, cargo.capacity);
    }

    private static Color tierColor(String tier) {
        switch (tier) {
            case "UNCOMMON": return TIER_UNCOMMON;
            case "RARE":     return TIER_RARE;
            case "EXOTIC":   return TIER_EXOTIC;
            case "ALIEN":    return TIER_ALIEN;
            default:         return Color.WHITE;
        }
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(Locale.US, pattern, args);
    }

    private static String formatId(String id) {
        String[] parts = id.split("[_\\-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase(Locale.US));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Screen lifecycle
    // ------------------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        starfield.update(delta);

        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        backgroundCamera.setToOrtho(false, w, h);

        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(backgroundCamera.combined);
        batch.begin();
        starfield.render(batch);
        batch.draw(overlayTexture, 0, 0, w, h);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        starfield.resize(width, height);
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        eventBus.unsubscribe(TradeCompletedEvent.class, onTradeCompleted);
        eventBus.unsubscribe(TradeFailedEvent.class,    onTradeFailed);
        eventBus.unsubscribe(WalletChangedEvent.class,  onWalletChanged);
        eventBus.unsubscribe(CargoChangedEvent.class,   onCargoChanged);
        stage.dispose();
        if (overlayTexture != null) overlayTexture.dispose();
    }
}
