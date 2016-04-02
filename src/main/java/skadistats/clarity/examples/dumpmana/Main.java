package skadistats.clarity.examples.dumpmana;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;

@UsesEntities
public class Main {

    private final Logger log = LoggerFactory.getLogger(Main.class.getPackage().getClass());

    private FieldPath hero_x;
    private FieldPath hero_y;

    private boolean isHero(Entity e) {
	boolean a;
	a = e.getDtClass().getDtName().startsWith("DT_DOTA_Unit_Hero");
	return a;
    }

    private void ensureFieldPaths(Entity e) {
        if (hero_x == null) {
			System.out.println("test");
            hero_x = e.getDtClass().getFieldPathForName("m_cellX");
            hero_y = e.getDtClass().getFieldPathForName("m_cellY");
        }
    }

    @OnEntityCreated
    public void onCreated(Context ctx, Entity e) {
if (!isHero(e)) {
            return;
        }
        System.out.format("Woooooooooooh%s", Integer.toString(ctx.getTick()));
		ensureFieldPaths(e);
		System.out.format("%s", e.getDtClass().getDtName());
        System.out.format("%s (%s/%s)\n", e.getDtClass().getDtName(), e.getPropertyForFieldPath(hero_x), e.getPropertyForFieldPath(hero_y));
    }

    @OnEntityUpdated
    public void onUpdated(Context ctx, Entity e, FieldPath[] updatedPaths, int updateCount) {
	if (!isHero(e)) {
            return;
        }
        ensureFieldPaths(e);
        boolean update = false;
        for (int i = 0; i < updateCount; i++) {
            if (updatedPaths[i].equals(hero_x) || updatedPaths[i].equals(hero_y)) {
                update = true;
                break;
            }
        }
        if (update) {
            System.out.format("%s (%s/%s)\n", e.getDtClass().getDtName(), e.getPropertyForFieldPath(hero_x), e.getPropertyForFieldPath(hero_y));
        }
    }


    public void run(String[] args) throws Exception {
        new SimpleRunner(new MappedFileSource(args[0])).runWith(this);
    }

    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }

}
