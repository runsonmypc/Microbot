package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public class ColossalWyrmBasicCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(1652, 2931, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_START_LADDER_TRIGGER),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_BALANCE_1_TRIGGER, -1, 2926, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_BALANCE_1_TRIGGER, 1647, -1, Operation.GREATER_EQUAL, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_MONKEYBARS_1_TRIGGER, 1635, -1, Operation.LESS_EQUAL, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_LADDER_1_TRIGGER, 1628, -1, Operation.LESS_EQUAL, Operation.GREATER),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_END_ZIPLINE_TRIGGER)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 50;
	}

	@Override
	public boolean isObstacleComplete(int currentXp, int previousXp, long lastMovingTime, int waitDelay) {
		// Colossal Wyrm courses have multi-XP drop obstacles
		// We ignore XP checks and only rely on movement/animation
		if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
			return false;
		}
		
		// Check if we've waited long enough after movement stopped
		return System.currentTimeMillis() - lastMovingTime >= waitDelay;
	}
}
