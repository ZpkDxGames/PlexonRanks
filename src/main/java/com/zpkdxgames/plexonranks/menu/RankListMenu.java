package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.config.RuntimeSettings;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.service.RankService;
import com.zpkdxgames.plexonranks.service.RankupService;
import com.zpkdxgames.plexonranks.service.RenderService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Phase 3 progression path: event-driven refresh, explicit actions, no hidden right-click. */
public final class RankListMenu implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final RankService ranks;
    private final RankupService rankup;
    private final RenderService render;
    private final Set<UUID> activeViewers = new LinkedHashSet<>(), submitting = new LinkedHashSet<>();

    public RankListMenu(JavaPlugin plugin, ConfigManager configs, RankService ranks, RankupService rankup, RenderService render) {
        this.plugin = plugin; this.configs = configs; this.ranks = ranks; this.rankup = rankup; this.render = render;
    }

    public void open(Player player, int requestedPage) {
        if (!ranks.loaded(player.getUniqueId())) { ranks.load(player.getUniqueId()); return; }
        RuntimeSettings.RankMenu menu = configs.current().settings().rankMenu();
        List<Integer> slots = rankSlots(menu); List<Rank> visible = visible(player);
        int pages = Math.max(1, (int) Math.ceil(visible.size() / (double) Math.max(1, slots.size())));
        int page = Math.max(1, Math.min(pages, requestedPage));
        RankListHolder holder = new RankListHolder(page, pages);
        Inventory inv = Bukkit.createInventory(holder, menu.size(), configs.formatter().component(menu.title(), Map.of("page", String.valueOf(page), "pages", String.valueOf(pages))));
        holder.attach(inv); draw(player, holder, inv, visible, slots); player.openInventory(inv); activeViewers.add(player.getUniqueId());
    }

    public void openAround(Player player, String rankId) {
        RuntimeSettings.RankMenu menu = configs.current().settings().rankMenu(); List<Integer> slots = rankSlots(menu); List<Rank> visible = visible(player);
        int index = -1; for (int i = 0; i < visible.size(); i++) if (visible.get(i).id().equalsIgnoreCase(rankId)) { index = i; break; }
        open(player, index < 0 || slots.isEmpty() ? 1 : index / slots.size() + 1);
    }

    private void draw(Player player, RankListHolder holder, Inventory inv, List<Rank> visible, List<Integer> slots) {
        fill(inv); holder.clearRanks(); Rank current = current(player); Optional<Rank> next = next(player, current);
        List<RequirementProgress> nextProgress = next.map(r -> render.progress(player, r)).orElseGet(List::of);
        boolean ready = next.isPresent() && nextProgress.stream().allMatch(RequirementProgress::complete);
        int offset = (holder.page() - 1) * Math.max(1, slots.size());
        for (int i = 0; i < slots.size() && offset + i < visible.size(); i++) {
            Rank rank = visible.get(offset + i); RankState state = state(current, next, rank); int slot = slots.get(i);
            inv.setItem(slot, rankItem(player, current, rank, state, nextProgress, ready)); holder.rank(slot, rank.id(), state);
        }
        navigation(current, next, nextProgress, holder, inv);
    }

    private ItemStack rankItem(Player player, Rank current, Rank rank, RankState state, List<RequirementProgress> nextProgress, boolean ready) {
        String path = "rank-list.states." + state.name().toLowerCase(java.util.Locale.ROOT);
        String material = rank.menu().material().isBlank() ? configs.current().menus().getString(path + ".material", "PAPER") : rank.menu().material();
        boolean glow = rank.menu().glow() || configs.current().menus().getBoolean(path + ".glow", false);
        String name = rank.menu().name().isBlank() ? configs.current().menus().getString("rank-list.rank-template.name", "%rank_name%") : rank.menu().name();
        List<String> template = rank.menu().useGlobalTemplate() || rank.menu().lore().isEmpty() ? configs.current().menus().getStringList("rank-list.rank-template.lore") : rank.menu().lore();
        boolean achieved = state == RankState.COMPLETED || state == RankState.CURRENT || state == RankState.MAX;
        List<RequirementProgress> progress = state == RankState.NEXT ? nextProgress : render.staticProgress(rank, achieved);
        Map<String,String> values = new LinkedHashMap<>(render.placeholders(player, current, rank, progress, state));
        values.put("rank_id", rank.display().shortName()); values.put("player_rank_id", current.display().shortName()); values.put("status", RankPresentation.stateLabel(state, state == RankState.NEXT && ready));
        List<String> lore = new ArrayList<>(render.expand(template, rank, state, render.requirementLines(progress), RankPresentation.rewards(rank)));
        lore.removeIf(line -> line.toUpperCase(java.util.Locale.ROOT).contains("RIGHT-CLICK"));
        lore.add(""); lore.add("<!italic><white>Click for rank details.</white>");
        if (state == RankState.NEXT && ready) lore.add("<!italic><green>Ready to rank up — use the explicit action in details.</green>");
        return MenuItems.create(configs.formatter(), material, rank.menu().amount(), name, lore, glow, rank.menu().customModelData(), values);
    }

    private void openDetails(Player player, int originPage, String rankId) {
        Rank selected = configs.current().registry().byId(rankId).filter(Rank::enabled).orElse(null); if (selected == null) { open(player, originPage); return; }
        Rank current = current(player); Optional<Rank> next = next(player, current); RankState state = state(current, next, selected);
        List<RequirementProgress> progress = state == RankState.NEXT ? render.progress(player, selected) : render.staticProgress(selected, state != RankState.LOCKED);
        boolean ready = state == RankState.NEXT && progress.stream().allMatch(RequirementProgress::complete);
        RankListHolder holder = RankListHolder.detail(originPage, selected.id(), current.id(), next.map(Rank::id).orElse(""));
        Inventory inv = Bukkit.createInventory(holder, 54, configs.formatter().component("<gradient:#4158D0:#C850C0><bold>RANK DETAILS</bold></gradient>")); holder.attach(inv); fill54(inv);
        inv.setItem(4, simple("NETHER_STAR", selected.display().name(), List.of("<!italic><gray>State</gray> <dark_gray>›</dark_gray> <white>" + RankPresentation.stateLabel(state, ready) + "</white>",
                "<!italic><gray>Position</gray> <dark_gray>›</dark_gray> <white>" + (configs.current().registry().position(selected) + 1) + " / " + configs.current().registry().ordered().size() + "</white>"), state == RankState.CURRENT || state == RankState.MAX));
        inv.setItem(20, simple("WRITABLE_BOOK", "<yellow><bold>REQUIREMENTS</bold></yellow>", requirementSummary(progress, state), false));
        inv.setItem(24, simple("CHEST", "<green><bold>REWARDS</bold></green>", rewardSummary(selected), false));
        inv.setItem(31, simple("COMPASS", "<aqua><bold>RELATIONSHIP</bold></aqua>", relationship(current, next, state), false));
        inv.setItem(48, simple("ARROW", "<yellow><bold>BACK TO PATH</bold></yellow>", List.of("<!italic><gray>Return to page " + originPage + ".</gray>"), false));
        inv.setItem(49, detailPrimary(selected, state, progress)); inv.setItem(50, simple("PLAYER_HEAD", "<aqua><bold>RANK DASHBOARD</bold></aqua>", List.of(), false));
        inv.setItem(51, simple(ready ? "LIME_DYE" : "PAPER", "<white><bold>" + RankPresentation.stateLabel(state, ready) + "</bold></white>", List.of(), ready));
        inv.setItem(52, simple("BARRIER", "<red><bold>CLOSE</bold></red>", List.of(), false)); player.openInventory(inv); activeViewers.add(player.getUniqueId());
    }

    private ItemStack detailPrimary(Rank rank, RankState state, List<RequirementProgress> progress) {
        if (state == RankState.NEXT && progress.stream().allMatch(RequirementProgress::complete)) return simple("LIME_CONCRETE", "<green><bold>RANK UP</bold></green>", List.of("<!italic><gray>Advance to</gray> " + rank.display().name(), "<!italic><green><bold>CLICK TO RANK UP</bold></green>"), true);
        if (state == RankState.NEXT) return simple("WRITABLE_BOOK", "<yellow><bold>VIEW REQUIREMENTS</bold></yellow>", List.of("<!italic><gray>Next: " + RankPresentation.blocker(progress) + "</gray>"), false);
        if (state == RankState.MAX) return simple("NETHER_STAR", "<gold><bold>MASTERY COMPLETE</bold></gold>", List.of(), true);
        return simple("COMPASS", "<aqua><bold>RANK DASHBOARD</bold></aqua>", List.of("<!italic><gray>Continue from your current progression.</gray>"), false);
    }

    private List<String> requirementSummary(List<RequirementProgress> progress, RankState state) {
        if (progress.isEmpty()) return List.of("<!italic><gray>No requirements.</gray>"); List<String> out = new ArrayList<>();
        for (int i=0;i<Math.min(5,progress.size());i++) { var c=RankPresentation.requirement(progress.get(i)); out.add((c.complete()?"<!italic><green>✔ </green>":"<!italic><yellow>◆ </yellow>")+"<gray>"+c.title()+"</gray> <dark_gray>›</dark_gray> <white>"+c.current()+" / "+c.required()+"</white>"); }
        if (progress.size()>5) out.add("<!italic><dark_gray>+"+(progress.size()-5)+" more</dark_gray>"); if (state==RankState.LOCKED) out.add("<!italic><gray>Complete earlier ranks first.</gray>"); return List.copyOf(out);
    }
    private List<String> rewardSummary(Rank rank) { List<String> all=RankPresentation.rewards(rank); List<String> out=new ArrayList<>(all.subList(0,Math.min(5,all.size()))); if(all.size()>5)out.add("<!italic><dark_gray>+"+(all.size()-5)+" more</dark_gray>");return List.copyOf(out); }
    private List<String> relationship(Rank current, Optional<Rank> next, RankState state) { return switch(state){case COMPLETED->List.of("<!italic><green>Already completed.</green>","<!italic><gray>Current:</gray> "+current.display().name());case CURRENT->List.of("<!italic><aqua>This is your current rank.</aqua>",next.map(r->"<!italic><gray>Next:</gray> "+r.display().name()).orElse("<!italic><gold>Progression mastered.</gold>"));case NEXT->List.of("<!italic><yellow>This is your next valid rank.</yellow>","<!italic><gray>Later ranks cannot be skipped.</gray>");case LOCKED->List.of("<!italic><gray>Complete the valid next step first.</gray>");case MAX->List.of("<!italic><gold>Maximum rank mastered.</gold>");}; }

    private void navigation(Rank current, Optional<Rank> next, List<RequirementProgress> progress, RankListHolder holder, Inventory inv) {
        int b=inv.getSize()-9; if(holder.page()>1)inv.setItem(b,simple("ARROW","<yellow><bold>PREVIOUS</bold></yellow>",List.of(),false));
        inv.setItem(b+2,simple("CLOCK","<white><bold>REFRESH</bold></white>",List.of("<!italic><gray>Refresh live progress now.</gray>"),false)); inv.setItem(b+3,simple("PLAYER_HEAD","<aqua><bold>RANK DASHBOARD</bold></aqua>",List.of(),false));
        boolean ready=next.isPresent()&&progress.stream().allMatch(RequirementProgress::complete);
        inv.setItem(b+4,next.isEmpty()?simple("NETHER_STAR","<gold><bold>MASTERY COMPLETE</bold></gold>",List.of(),true):ready?simple("LIME_CONCRETE","<green><bold>RANK UP</bold></green>",List.of("<!italic><green><bold>CLICK TO RANK UP</bold></green>"),true):simple("WRITABLE_BOOK","<yellow><bold>VIEW REQUIREMENTS</bold></yellow>",List.of("<!italic><gray>Next: "+RankPresentation.blocker(progress)+"</gray>"),false));
        inv.setItem(b+6,simple(ready?"LIME_DYE":"PAPER","<white><bold>PAGE "+holder.page()+" / "+holder.pages()+"</bold></white>",List.of("<!italic><gray>Current Rank</gray> <dark_gray>›</dark_gray> "+current.display().name()),ready)); inv.setItem(b+7,simple("BARRIER","<red><bold>CLOSE</bold></red>",List.of(),false)); if(holder.page()<holder.pages())inv.setItem(b+8,simple("ARROW","<yellow><bold>NEXT</bold></yellow>",List.of(),false));
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RankListHolder holder)) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot()<0 || event.getRawSlot()>=event.getView().getTopInventory().getSize()) return; int slot=event.getRawSlot();
        if(holder.view()==RankListHolder.View.DETAIL){detailClick(player,holder,slot);return;} String rankId=holder.rankAt(slot);if(rankId!=null){openDetails(player,holder.page(),rankId);return;}
        int b=event.getView().getTopInventory().getSize()-9; if(slot==b&&holder.page()>1)open(player,holder.page()-1);else if(slot==b+2)open(player,holder.page());else if(slot==b+3)player.performCommand("rank");else if(slot==b+4)pathPrimary(player);else if(slot==b+7)player.closeInventory();else if(slot==b+8&&holder.page()<holder.pages())open(player,holder.page()+1);
    }

    private void detailClick(Player player, RankListHolder holder, int slot) {
        if(slot==48){open(player,holder.originPage());return;} if(slot==50){player.performCommand("rank");return;} if(slot==52){player.closeInventory();return;} if(slot!=49)return;
        Rank selected=configs.current().registry().byId(holder.selectedRankId()).orElse(null);if(selected==null){open(player,holder.originPage());return;} Rank current=current(player);Optional<Rank> next=next(player,current);
        if(!holder.matches(current.id(),next.map(Rank::id).orElse(""))){player.sendActionBar(configs.formatter().component("<yellow>Your rank changed while this view was open. The path was refreshed.</yellow>"));openAround(player,current.id());return;}
        if(next.map(r->r.id().equals(selected.id())).orElse(false)){List<RequirementProgress> progress=render.progress(player,selected);if(progress.stream().allMatch(RequirementProgress::complete))submit(player);else player.performCommand("rank requirements");}else player.performCommand("rank");
    }

    private void pathPrimary(Player player){Rank current=current(player);Optional<Rank> next=next(player,current);if(next.isEmpty()){openAround(player,current.id());return;}List<RequirementProgress> progress=render.progress(player,next.get());if(progress.stream().allMatch(RequirementProgress::complete))submit(player);else player.performCommand("rank requirements");}
    private void submit(Player player){UUID id=player.getUniqueId();if(!submitting.add(id))return;player.closeInventory();try{rankup.attempt(player);}finally{submitting.remove(id);}}
    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof RankListHolder&&e.getRawSlots().stream().anyMatch(s->s<e.getView().getTopInventory().getSize()))e.setCancelled(true);}
    @EventHandler public void onClose(InventoryCloseEvent e){if(!(e.getInventory().getHolder() instanceof RankListHolder)||!(e.getPlayer() instanceof Player p))return;Bukkit.getScheduler().runTask(plugin,()->{if(!(p.getOpenInventory().getTopInventory().getHolder() instanceof RankListHolder))activeViewers.remove(p.getUniqueId());});}
    @EventHandler public void onQuit(PlayerQuitEvent e){activeViewers.remove(e.getPlayer().getUniqueId());submitting.remove(e.getPlayer().getUniqueId());}

    /** Compatibility lifecycle hook. Legacy refresh settings remain parsed, but no repeating task exists. */
    public void reload(){for(UUID id:List.copyOf(activeViewers)){Player p=Bukkit.getPlayer(id);if(p==null||!p.isOnline()){activeViewers.remove(id);continue;}Inventory top=p.getOpenInventory().getTopInventory();if(top.getHolder() instanceof RankListHolder h){if(h.view()==RankListHolder.View.PATH)open(p,h.page());else openDetails(p,h.originPage(),h.selectedRankId());}}}
    public void stop(){activeViewers.clear();submitting.clear();}
    public int activeViewerCount(){return activeViewers.size();}

    private List<Integer> rankSlots(RuntimeSettings.RankMenu menu){int b=menu.size()-9;Set<Integer> reserved=Set.of(b,b+2,b+3,b+4,b+6,b+7,b+8);return menu.rankSlots().stream().filter(s->s>=0&&s<menu.size()&&!reserved.contains(s)).toList();}
    private List<Rank> visible(Player p){return configs.current().registry().visible().stream().filter(r->r.bypassPermission().isBlank()||p.hasPermission(r.bypassPermission())).toList();}
    private Rank current(Player p){return ranks.current(p.getUniqueId()).orElse(configs.current().registry().defaultRank());}
    private Optional<Rank> next(Player p,Rank current){return configs.current().registry().nextAccessible(current,p::hasPermission);}
    private RankState state(Rank current,Optional<Rank> next,Rank rank){if(rank.id().equals(current.id()))return next.isEmpty()?RankState.MAX:RankState.CURRENT;if(rank.order()<current.order())return RankState.COMPLETED;if(next.map(r->r.id().equals(rank.id())).orElse(false))return RankState.NEXT;return RankState.LOCKED;}
    private void fill(Inventory inv){RuntimeSettings.RankMenu menu=configs.current().settings().rankMenu();if(!menu.fillerEnabled()){inv.clear();return;}ItemStack fill=MenuItems.create(configs.formatter(),menu.fillerMaterial(),1,menu.fillerName(),List.of(),false,0,Map.of());for(int i=0;i<inv.getSize();i++)inv.setItem(i,fill);}
    private void fill54(Inventory inv){for(int i=0;i<54;i++){int r=i/9,c=i%9;inv.setItem(i,simple(r==0||r==5||c==0||c==8?"GRAY_STAINED_GLASS_PANE":"BLACK_STAINED_GLASS_PANE","<black> </black>",List.of(),false));}}
    private ItemStack simple(String material,String name,List<String> lore,boolean glow){return MenuItems.create(configs.formatter(),material,1,"<!italic>"+name,lore,glow,0,Map.of());}
}
