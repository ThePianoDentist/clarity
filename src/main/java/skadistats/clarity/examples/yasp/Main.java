package skadistats.clarity.examples.yasp;

import com.google.gson.Gson;
import com.google.protobuf.GeneratedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.FileUtils;
import skadistats.clarity.decoder.Util;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.s1.GameRulesStateType;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.OnEntityEntered;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.CombatLog;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnMessage;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.wire.common.proto.Demo.CDemoFileInfo;
import skadistats.clarity.wire.common.proto.Demo.CGameInfo.CDotaGameInfo.CPlayerInfo;
import skadistats.clarity.wire.common.proto.DotaUserMessages.CDOTAUserMsg_ChatEvent;
import skadistats.clarity.wire.common.proto.DotaUserMessages.CDOTAUserMsg_LocationPing;
import skadistats.clarity.wire.common.proto.DotaUserMessages.CDOTAUserMsg_SpectatorPlayerUnitOrders;
import skadistats.clarity.wire.common.proto.DotaUserMessages.DOTA_COMBATLOG_TYPES;
import skadistats.clarity.wire.s2.proto.S2UserMessages.CUserMessageSayText2;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private final Logger log = LoggerFactory.getLogger(Main.class.getPackage().getClass());
    float INTERVAL = 1;
    float nextInterval = 0;
    Integer time = 0;
    int numPlayers = 10;
    int[] validIndices = new int[numPlayers];
    boolean init = false;
    int gameStartTime = 0;
    int gameEndTime;
    private Gson g = new Gson();
    HashMap<String, Integer> name_to_slot = new HashMap<String, Integer>();
    HashMap<Integer, Integer> slot_to_playerslot = new HashMap<Integer, Integer>();

    List<hero_position_dict> all_hero_positions = new ArrayList<>();
    List<hero_position_dict> radiant_all_hero_positions = new ArrayList<>();
    List<hero_position_dict> dire_all_hero_positions = new ArrayList<>();

    private class hero_position{
        public hero_position(Integer hero_x, Integer hero_y, Integer time, Integer life_state, Boolean has_bots) {
            this.time = time;
            this.hero_x = hero_x;
            this.hero_y = hero_y;
            this.has_bots = has_bots;
            this.life_state = life_state;
        }

        public Integer getTime() {
            return time;
        }

        public Integer getHero_x() {
            return hero_x;
        }

        public void setHero_x(Integer hero_x) {
            this.hero_x = hero_x;
        }

        public Integer getHero_y() {
            return hero_y;
        }

        public void setHero_y(Integer hero_y) {
            this.hero_y = hero_y;
        }

        public void setTime(Integer time) {
            this.time = time;
        }

        public Boolean getHas_bots() {
            return has_bots;
        }

        public void setHas_bots(Boolean has_bots) {
            this.has_bots = has_bots;
        }

        public int getLife_state() {
            return life_state;
        }

        public void setLife_state(int life_state) {
            this.life_state = life_state;
        }

        private Integer time;
        private Integer hero_x;
        private Integer hero_y;
        private Boolean has_bots;
        private int life_state;
    }

    private class hero_position_dict{
        public hero_position getHero_position_by_time(int chosen_time) throws Exception {
            for (hero_position hero_pos : hero_positions){
                if (hero_pos.getTime() == chosen_time){
                    return hero_pos;
                }
            }
            throw new Exception("Time not in hero positions");
        }

        public hero_position getHero_position_by_index(int index) {
            return hero_positions.get(index);
        }

        public List<hero_position> getHero_positions() {
            return hero_positions;
        }

        public List<hero_position> getHero_positions(Integer start_time, Integer end_time) {
            List<hero_position> filtered_hero_positions = new ArrayList<>();
            for (hero_position each_hero_position: hero_positions){
                if (each_hero_position.getTime() >= start_time && each_hero_position.getTime() < end_time){
                    filtered_hero_positions.add(each_hero_position);
                }
            }
            return filtered_hero_positions;
        }

        public void setHero_positions(List<hero_position> hero_positions) {
            this.hero_positions = hero_positions;
        }

        public String getHero_string() {
            return hero_string;
        }

        public void setHero_string(String hero_string) {
            this.hero_string = hero_string;
        }

        public void update_hero_positions(hero_position new_hero_position){
            hero_positions.add(new_hero_position);
        }

        public hero_position_dict(String hero_string, Long steamid, List<hero_position> hero_positions) {
            this.hero_positions = hero_positions;
            this.hero_string = hero_string;
            this.steamid = steamid;
        }

        private String hero_string;
        private Long steamid;
        private List<hero_position> hero_positions;
    }

    public class all_heros_distance_travelled{
        private String hero_string;
        private String player_string;

    }

    public class hero_ulti_cooldown{
        public int get_itime() {
            return itime;
        }

        public void set_itime(int itime) {
            this.itime = itime;
        }

        public boolean get_ulti_is_cooldown() {
            return ulti_is_cooldown;
        }

        public void set_ulti_is_cooldown(boolean ulti_is_cooldown) {
            this.ulti_is_cooldown = ulti_is_cooldown;
        }

        private String hero_name;
        private int itime;
        private boolean ulti_is_cooldown;
    }

    private class Entry {
        public Integer time;
        public String type;
        public Integer team;
        public String unit;
        public String key;
        public Integer value;
        public Integer slot;
        public Integer player_slot;
        public Long steam_id;
        //chat event fields
        public Integer player1;
        public Integer player2;
        //combat log fields
        public String attackername;
        public String targetname;
        public String sourcename;
        public String targetsourcename;
        public Boolean attackerhero;
        public Boolean targethero;
        public Boolean attackerillusion;
        public Boolean targetillusion;
        public String inflictor;
        public Integer gold_reason;
        public Integer xp_reason;
        public String valuename;
        //entity fields
        public Integer gold;
        public Integer lh;
        public Integer xp;
        public Integer x;
        public Integer y;
        public Float stuns;
        public Integer hero_id;
        public Integer life_state;
        public Integer level;
        public Integer kills;
        public Integer deaths;
        public Integer assists;
        public Integer denies;
        //public Boolean hasPredictedVictory;
    
        public Entry() {
        }
        
        public Entry(Integer time) {
            this.time = time;
        }
    }

    public void output(Entry e) {
        System.out.print(g.toJson(e) + "\n");
    }

    //@OnMessage(GeneratedMessage.class)
    public void onMessage(Context ctx, GeneratedMessage message) {
        System.err.println(message.getClass().getName());
        System.out.println(message.toString());
    }

    /*
    //@OnMessage(CDOTAUserMsg_SpectatorPlayerClick.class)
    public void onSpectatorPlayerClick(Context ctx, CDOTAUserMsg_SpectatorPlayerClick message){
        Entry entry = new Entry(time);
        entry.type = "clicks";
        //need to get the entity by index
        entry.key = String.valueOf(message.getOrderType());
        //theres also target_index
        //output(entry);
    }
    */

    @OnMessage(CDOTAUserMsg_SpectatorPlayerUnitOrders.class)
    public void onSpectatorPlayerUnitOrders(Context ctx, CDOTAUserMsg_SpectatorPlayerUnitOrders message) {
        Entry entry = new Entry(time);
        entry.type = "actions";
        //the entindex points to a CDOTAPlayer.  This is probably the player that gave the order.
        Entity e = ctx.getProcessor(Entities.class).getByIndex(message.getEntindex());
        entry.slot = getEntityProperty(e, "m_iPlayerID", null);
        //Integer handle = (Integer)getEntityProperty(e, "m_hAssignedHero", null);
        //Entity h = ctx.getProcessor(Entities.class).getByHandle(handle);
        //System.err.println(h.getDtClass().getDtName());
        //break actions into types?
        entry.key = String.valueOf(message.getOrderType());
        //System.err.println(message);
        //output(entry);
    }


    @OnMessage(CDOTAUserMsg_LocationPing.class)
    public void onPlayerPing(Context ctx, CDOTAUserMsg_LocationPing message) {
        Entry entry = new Entry(time);
        entry.type = "pings";
        entry.slot = message.getPlayerId();
        /*
        System.err.println(message);
        player_id: 7
        location_ping {
          x: 5871
          y: 6508
          target: -1
          direct_ping: false
          type: 0
        }
        */
        //we could get the ping coordinates/type if we cared
        //entry.key = String.valueOf(message.getOrderType());
        //output(entry);
    }

    @OnMessage(CDOTAUserMsg_ChatEvent.class)
    public void onChatEvent(Context ctx, CDOTAUserMsg_ChatEvent message) {
        Integer player1 = message.getPlayerid1();
        Integer player2 = message.getPlayerid2();
        Integer value = message.getValue();
        String type = String.valueOf(message.getType());
        Entry entry = new Entry(time);
        entry.type = type;
        entry.player1 = player1;
        entry.player2 = player2;
        entry.value = value;
        //output(entry);
    }

    /*
    @OnMessage(CUserMsg_SayText2.class)
    public void onAllChatS1(Context ctx, CUserMsg_SayText2 message) {
        Entry entry = new Entry(time);
        entry.unit =  String.valueOf(message.getPrefix());
        entry.key =  String.valueOf(message.getText());
        entry.type = "chat";
        //output(entry);
    }
    */

    @OnMessage(CUserMessageSayText2.class)
    public void onAllChatS2(Context ctx, CUserMessageSayText2 message) {
        Entry entry = new Entry(time);
        entry.unit = String.valueOf(message.getParam1());
        entry.key = String.valueOf(message.getParam2());
        Entity e = ctx.getProcessor(Entities.class).getByIndex(message.getEntityindex());
        entry.slot = getEntityProperty(e, "m_iPlayerID", null);
        entry.type = "chat";
        //output(entry);
    }

    @OnMessage(CDemoFileInfo.class)
    public void onFileInfo(Context ctx, CDemoFileInfo message) {
        //beware of 4.2b limit!  we don't currently do anything with this, so we might be able to just remove this
        //we can't use the value field since it takes Integers
        //Entry matchIdEntry = new Entry();
        //matchIdEntry.type = "match_id";
        //matchIdEntry.value = message.getGameInfo().getDota().getMatchId();
        ////output(matchIdEntry);

        //emit epilogue event to mark finish
        Entry epilogueEntry = new Entry();
        epilogueEntry.type = "epilogue";
        epilogueEntry.key = new Gson().toJson(message);
        //output(epilogueEntry);
    }

    @OnCombatLogEntry
    public void onCombatLogEntry(Context ctx, CombatLogEntry cle) {
        time = Math.round(cle.getTimestamp());
        //create a new entry
        Entry combatLogEntry = new Entry(time);
        combatLogEntry.type = cle.getType().name();
        //translate the fields using string tables if necessary (get*Name methods)
        combatLogEntry.attackername = cle.getAttackerName();
        combatLogEntry.targetname = cle.getTargetName();
        combatLogEntry.sourcename = cle.getDamageSourceName();
        combatLogEntry.targetsourcename = cle.getTargetSourceName();
        combatLogEntry.inflictor = cle.getInflictorName();
        combatLogEntry.gold_reason = cle.getGoldReason();
        combatLogEntry.xp_reason = cle.getXpReason();
        combatLogEntry.attackerhero = cle.isAttackerHero();
        combatLogEntry.targethero = cle.isTargetHero();
        combatLogEntry.attackerillusion = cle.isAttackerIllusion();
        combatLogEntry.targetillusion = cle.isTargetIllusion();
        combatLogEntry.value = cle.getValue();

        //value may be out of bounds in string table, we can only get valuename if a purchase (type 11)
        if (cle.getType() == DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_PURCHASE) {
            combatLogEntry.valuename = cle.getValueName();
        }
        output(combatLogEntry);
        try {
            save_json_to_file(g.toJson(combatLogEntry), "combat_log");
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (cle.getType().ordinal() > 19) {
            System.err.println(cle);
        }
    }

    @OnEntityEntered
    public void onEntityEntered(Context ctx, Entity e) {
        //CDOTA_NPC_Observer_Ward
        //CDOTA_NPC_Observer_Ward_TrueSight
        //s1 "DT_DOTA_NPC_Observer_Ward"
        //s1 "DT_DOTA_NPC_Observer_Ward_TrueSight"
        boolean isObserver = e.getDtClass().getDtName().equals("CDOTA_NPC_Observer_Ward");
        boolean isSentry = e.getDtClass().getDtName().equals("CDOTA_NPC_Observer_Ward_TrueSight");
        if (isObserver || isSentry) {
            //System.err.println(e);
            Entry entry = new Entry(time);
            Integer x = getEntityProperty(e, "CBodyComponent.m_cellX", null);
            Integer y = getEntityProperty(e, "CBodyComponent.m_cellY", null);
            Integer[] pos = {x, y};
            entry.type = isObserver ? "obs" : "sen";
            entry.key = Arrays.toString(pos);
            //System.err.println(entry.key);
            Integer owner = getEntityProperty(e, "m_hOwnerEntity", null);
            Entity ownerEntity = ctx.getProcessor(Entities.class).getByHandle(owner);
            entry.slot = ownerEntity != null ? (Integer) getEntityProperty(ownerEntity, "m_iPlayerID", null) : null;
            //2/3 radiant/dire
            //entry.team = e.getProperty("m_iTeamNum");
            //output(entry);
        }
    }

    @UsesEntities
    @OnTickStart
    public void onTickStart(Context ctx, boolean synthetic) {
        //s1 DT_DOTAGameRulesProxy
        Entity grp = ctx.getProcessor(Entities.class).getByDtName("CDOTAGamerulesProxy");
        Entity pr = ctx.getProcessor(Entities.class).getByDtName("CDOTA_PlayerResource");
        Entity dData = ctx.getProcessor(Entities.class).getByDtName("CDOTA_DataDire");
        Entity rData = ctx.getProcessor(Entities.class).getByDtName("CDOTA_DataRadiant");
        if (grp != null) {
            //System.err.println(grp);
            //dota_gamerules_data.m_iGameMode = 22
            //dota_gamerules_data.m_unMatchID64 = 1193091757
            time = Math.round((float) getEntityProperty(grp, "m_pGameRules.m_fGameTime", null));
            //alternate to combat log for getting game zero time (looks like this is set at the same time as the game start, so it's not any better for streaming)
            /*
            int currGameStartTime = Math.round( (float) grp.getProperty("m_pGameRules.m_flGameStartTime"));
            if (currGameStartTime != gameStartTime){
                gameStartTime = currGameStartTime;
                System.err.println(gameStartTime);
                System.err.println(time);
            }
            */
            gameStartTime = Math.round( (float) grp.getProperty("m_pGameRules.m_flGameStartTime"));
            gameEndTime = Math.round( (float) grp.getProperty("m_pGameRules.m_flGameEndTime"));

        }
        if (pr != null) {
            //Radiant coach shows up in vecPlayerTeamData as position 5
            //all the remaining dire entities are offset by 1 and so we miss reading the last one and don't get data for the first dire player
            //coaches appear to be on team 1, radiant is 2 and dire is 3?
            //construct an array of valid indices to get vecPlayerTeamData from
            if (!init) {
                int added = 0;
                int i = 0;
                //according to @Decoud Valve seems to have fixed this issue and players should be in first 10 slots again
                //sanity check of i to prevent infinite loop when <10 players?
                while (added < numPlayers && i < 100) {
                    try {
                        //check each m_vecPlayerData to ensure the player's team is radiant or dire
                        int playerTeam = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerTeam", i);
                        int teamSlot = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iTeamSlot", i);
                        Long steamid = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerSteamID", i);
                        //System.err.format("%s %s %s: %s\n", i, playerTeam, teamSlot, steamid);
                        if (playerTeam == 2 || playerTeam == 3) {
                            //output the player_slot based on team and teamslot
                            Entry entry = new Entry(time);
                            entry.type = "player_slot";
                            entry.key = String.valueOf(added);
                            entry.value = (playerTeam == 2 ? 0 : 128) + teamSlot;
                            entry.steam_id = steamid;
                            //output(entry);
                            //add it to validIndices, add 1 to added
                            validIndices[added] = i;
                            added += 1;
                            slot_to_playerslot.put(added, entry.value);
                        }
                    }
                    catch(Exception e) {
                        //swallow the exception when an unexpected number of players (!=10)
                        //System.err.println(e);
                    }

                    i += 1;
                }
                init = true;
            }

            if (time >= nextInterval) {
                //System.err.println(pr);
                for (int i = 0; i < numPlayers; i++) {
                    Integer hero = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_nSelectedHeroID", validIndices[i]);
                    int handle = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_hSelectedHero", validIndices[i]);
                    int playerTeam = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerTeam", validIndices[i]);
                    int teamSlot = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iTeamSlot", validIndices[i]);
                    Long steamid = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerSteamID", validIndices[i]);
                    System.err.format("hero:%s i:%s teamslot:%s playerteam:%s\n", hero, i, teamSlot, playerTeam);

                    //2 is radiant, 3 is dire, 1 is other?
                    Entity dataTeam = playerTeam == 2 ? rData : dData;

                    Entry entry = new Entry(time);
                    entry.type = "interval";
                    entry.slot = i;

                    if (teamSlot >= 0) {
                        entry.gold = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedGold", teamSlot);
                        entry.lh = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iLastHitCount", teamSlot);
                        entry.xp = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedXP", teamSlot);
                        entry.stuns = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_fStuns", teamSlot);
                    }

                    try{
                        entry.level = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iLevel", validIndices[i]);
                        entry.kills = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iKills", validIndices[i]);
                        entry.deaths = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iDeaths", validIndices[i]);
                        entry.assists = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iAssists", validIndices[i]);
                        entry.denies = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iDenyCount", teamSlot);
                    }
                    catch(Exception e){
                        //swallow exceptions encountered while trying to get these additional values
                        //System.err.println(e);
                    }
                    //TODO: gem, rapier time?
                    //https://github.com/yasp-dota/yasp/issues/333
                    //need to dump inventory items for each player and possibly keep track of item entity handles

                    //get the player's hero entity
                    Entity e = ctx.getProcessor(Entities.class).getByHandle(handle);
                    //get the hero's coordinates
                    if (e != null) {
                        //System.err.println(e);
                        entry.x = getEntityProperty(e, "CBodyComponent.m_cellX", null);
                        entry.y = getEntityProperty(e, "CBodyComponent.m_cellY", null);
                        //System.err.format("%s, %s\n", entry.x, entry.y);
                        //get the hero's entity name, ex: CDOTA_Hero_Zuus
                        entry.unit = e.getDtClass().getDtName();
                        entry.hero_id = hero;
                        entry.life_state = getEntityProperty(e, "m_lifeState", null);
                        //System.err.format("%s: %s\n", entry.unit, entry.life_state);
                        //check if hero has been assigned to entity
                        if (hero > 0) {
                            //get the hero's entity name, ex: CDOTA_Hero_Zuus
                            String unit = e.getDtClass().getDtName();
                            //grab the end of the name, lowercase it
                            String ending = unit.substring("CDOTA_Unit_Hero_".length());
                            //valve is bad at consistency and the combat log name could involve replacing camelCase with _ or not!
                            //double map it so we can look up both cases
                            String combatLogName = "npc_dota_hero_" + ending.toLowerCase();
                            //don't include final underscore here since the first letter is always capitalized and will be converted to underscore
                            String combatLogName2 = "npc_dota_hero" + ending.replaceAll("([A-Z])", "_$1").toLowerCase();
                            //System.err.format("%s, %s, %s\n", unit, combatLogName, combatLogName2);
                            //populate for combat log mapping
                            name_to_slot.put(combatLogName, entry.slot);
                            name_to_slot.put(combatLogName2, entry.slot);
                            hero_position position_data = new hero_position(entry.x, entry.y, time,
                                    entry.life_state, false);

                            update_all_hero_positions_variants(all_hero_positions, unit, position_data, steamid);
                            if (playerTeam == 2){
                                update_all_hero_positions_variants(radiant_all_hero_positions, unit, position_data, steamid);
                            }
                            else{
                                update_all_hero_positions_variants(dire_all_hero_positions, unit, position_data, steamid);
                            }


                        }
                    }
                    //output(entry);

                }
                nextInterval += INTERVAL;
            }
        }
    }

    public <T> T getEntityProperty(Entity e, String property, Integer idx) {
        if (e == null) {
            return null;
        }
        if (idx != null) {
            property = property.replace("%i", Util.arrayIdxToString(idx));
        }
        FieldPath fp = e.getDtClass().getFieldPathForName(property);
        return e.getPropertyForFieldPath(fp);
    }

    public void update_all_hero_positions_variants(List<hero_position_dict> hero_positions_list_variant,
                                                   String unit, hero_position position_data, Long steamid) {
        boolean updated = false;
        for (hero_position_dict hero_pos : hero_positions_list_variant) {
            if (hero_pos.getHero_string() == unit) {
                hero_pos.update_hero_positions(position_data);
                updated = true;
                break;
            }
        }
        if (!updated) {
            hero_positions_list_variant.add(new hero_position_dict(unit, steamid,
                    new ArrayList<>(Arrays.asList(position_data))));
        }
    };

    public Double distance_modulus(Integer x2, Integer x1, Integer y2, Integer y1) {
        return Math.sqrt(Math.pow(x2 - x1, 2) +
                Math.pow(y2 - y1, 2));
    }

    public void calculate_player_rat_factor(List<hero_position_dict> list_all_hero_positions, Long steamid){

    }

    public HashMap<Integer, Double> calculate_hey_lets_all_circlejerk_mid_factor
            (List<hero_position_dict> the_hero_positions) throws Exception {
        HashMap<Integer, Double> average_distances_over_time = new HashMap<>();
        for (int current_time = gameStartTime; current_time < gameEndTime; current_time++){
            Double total_distance = 0.0;
            int hero_position_time_one = 0;
            int hero_position_time_two;
            int comparisons = 0;
            for (int k=0; k < 4; k++) {
                for (int l=1; l < 5; l++) {
                    if (l > k) {
                        hero_position hero_position_one = the_hero_positions.get(k).getHero_position_by_time(current_time);
                        hero_position hero_position_two = the_hero_positions.get(l).getHero_position_by_time(current_time);
                        if (hero_position_one.getLife_state() == 0 && hero_position_two.getLife_state() == 0) {
                            hero_position_time_one = the_hero_positions.get(k).
                                    getHero_position_by_time(current_time).getTime();
                            hero_position_time_two = the_hero_positions.get(l).
                                    getHero_position_by_time(current_time).getTime();
                            if (hero_position_time_one != hero_position_time_two) {
                                throw new Exception("times do not sync for circlejerk");
                            }
                            Double distance_between_two_hero = distance_modulus(hero_position_two.getHero_x(), hero_position_one.getHero_x(),
                                    hero_position_two.getHero_y(), hero_position_one.getHero_y());
                            total_distance += distance_between_two_hero;
                            comparisons += 1;
                        }
                        else{
                            break;
                        }
                    }
                }
            }
            if (comparisons > 0){  // If there are no comparisons. all dead, teamwiped.we dont want to save tat right? doesnt make sense
                average_distances_over_time.put(current_time, total_distance/comparisons);
            }
        }
        return average_distances_over_time;
    }

    public class hey_lets_all_circlejerk_mid_factor {
        private HashMap<Integer, Double> average_distances_over_time = new HashMap<>();

        private HashMap<Integer, Double> set_average_distance_over_time(List<hero_position_dict> the_hero_positions) throws Exception{
            for (int current_time = gameStartTime; current_time < gameEndTime; current_time++){
                Double total_distance = 0.0;
                int hero_position_time_one = 0;
                int hero_position_time_two;
                int comparisons = 0;
                for (int k=0; k < 4; k++) {
                    for (int l=1; l < 5; l++) {
                        if (l > k) {
                            hero_position hero_position_one = the_hero_positions.get(k).getHero_position_by_time(current_time);
                            hero_position hero_position_two = the_hero_positions.get(l).getHero_position_by_time(current_time);
                            if (hero_position_one.getLife_state() == 0 && hero_position_two.getLife_state() == 0) {
                                hero_position_time_one = the_hero_positions.get(k).
                                        getHero_position_by_time(current_time).getTime();
                                hero_position_time_two = the_hero_positions.get(l).
                                        getHero_position_by_time(current_time).getTime();
                                if (hero_position_time_one != hero_position_time_two) {
                                    throw new Exception("times do not sync for circlejerk");
                                }
                                Double distance_between_two_hero = distance_modulus(hero_position_two.getHero_x(), hero_position_one.getHero_x(),
                                        hero_position_two.getHero_y(), hero_position_one.getHero_y());
                                total_distance += distance_between_two_hero;
                                comparisons += 1;
                            }
                            else{
                                break;
                            }
                        }
                    }
                }
                if (comparisons > 0){  // If there are no comparisons. all dead, teamwiped.we dont want to save tat right? doesnt make sense
                    Double put = average_distances_over_time.put(current_time, total_distance / comparisons);
                }
            }
            return average_distances_over_time;
        }
    }

    public void calculate_ulti_efficiency(){

    }

    public void calculate_tp_save_factor(){

    }

    public void calculate_davai_factor(){

    }

    public void calculate_activity(List<hero_position_dict> the_hero_positions,
                                   Integer start_time, Integer end_time){
        for (hero_position_dict hero_position_dict_a : the_hero_positions){
            String hero_name = hero_position_dict_a.getHero_string();
            List<hero_position> hero_position_list = hero_position_dict_a.getHero_positions(start_time, end_time);
            Double distance_sum = 0.0;
            for (int j = 1; j < hero_position_list.size(); j++){
                hero_position new_position = hero_position_list.get(j);
                hero_position old_position = hero_position_list.get(j-1);
                Double distance_moved = Math.sqrt(Math.pow(new_position.getHero_x() - old_position.getHero_x(), 2) +
                        Math.pow(new_position.getHero_y() - old_position.getHero_y(), 2));
                distance_sum += distance_moved;
            }
        }
    }

    public void calculate_activity(List<hero_position_dict> the_hero_positions){
        calculate_activity(the_hero_positions, 0, 1000000); // Let's just hope no super long games lol
    }

    public void run(String[] args) throws Exception {
        long tStart = System.currentTimeMillis();
        new SimpleRunner(new MappedFileSource(args[0])).runWith(this);
        long tMatch = System.currentTimeMillis() - tStart;
        System.err.format("total time taken: %s\n", (tMatch) / 1000.0);
        Gson gson = new Gson();
        String final_hero_positions = gson.toJson(all_hero_positions);
        //System.out.println(final_hero_positions);

        Pattern p = Pattern.compile(".*?(\\d+).*?");
        Matcher m = p.matcher(args[0]);
        String match_id = "";
        while (m.find()) {
            match_id = m.group(1);
        }
        String file_string = "~/Documents/dota_hero_position_" + match_id + ".json";
        FileUtils.writeStringToFile(new File(file_string), final_hero_positions);
        calculate_activity(all_hero_positions);
        calculate_activity(radiant_all_hero_positions, 0, gameStartTime + 600);
        HashMap<Integer, Double> radiant_circljerkness_over_time = calculate_hey_lets_all_circlejerk_mid_factor
                (radiant_all_hero_positions);
        HashMap<Integer, Double> dire_circljerkness_over_time = calculate_hey_lets_all_circlejerk_mid_factor
                (dire_all_hero_positions);
        String radiant_circlejerk_save = "/home/jdog/Documents/clarity_data/radiant_circljerkness_over_time/" + match_id
                + ".json";
        String dire_circlejerk_save = "/home/jdog/Documents/clarity_data/dire_circljerkness_over_time/" + match_id
                + ".json";
        //System.out.println(radiant_circljerkness_over_time);
        FileUtils.writeStringToFile(new File(radiant_circlejerk_save), gson.toJson(radiant_circljerkness_over_time));
        FileUtils.writeStringToFile(new File(dire_circlejerk_save), gson.toJson(dire_circljerkness_over_time));
    }

    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }

    public void save_json_to_file(String json_stuff, String name) throws IOException {
        String file_string = "/home/jdog/Documents/clarity_data/random/" + name+ ".json";
        FileUtils.writeStringToFile(new File(file_string), json_stuff);
    }
}

