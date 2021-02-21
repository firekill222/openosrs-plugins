package net.runelite.client.plugins.quester.Generic;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.paistisuite.api.*;
import net.runelite.client.plugins.paistisuite.api.WebWalker.api_lib.DaxWalker;
import net.runelite.client.plugins.paistisuite.api.WebWalker.api_lib.WebWalkerServerApi;
import net.runelite.client.plugins.paistisuite.api.WebWalker.api_lib.models.PathResult;
import net.runelite.client.plugins.paistisuite.api.WebWalker.api_lib.models.PathStatus;
import net.runelite.client.plugins.paistisuite.api.WebWalker.api_lib.models.PlayerDetails;
import net.runelite.client.plugins.paistisuite.api.WebWalker.api_lib.models.Point3D;
import net.runelite.client.plugins.paistisuite.api.WebWalker.walker_engine.local_pathfinding.Reachable;
import net.runelite.client.plugins.paistisuite.api.WebWalker.wrappers.RSTile;
import net.runelite.client.plugins.paistisuite.api.types.PTileObject;
import net.runelite.client.plugins.quester.Quester;
import net.runelite.client.plugins.quester.Task;

import java.util.function.BooleanSupplier;

@Slf4j
public class InteractWithObjectTask implements Task {
    String objectName;
    String[] options;
    WorldPoint objectLoc;
    Quester plugin;
    BooleanSupplier successCondition;
    boolean isCompleted;
    boolean failed;
    boolean walkedToDestination = false;
    int interactAttempts = 0;

    public InteractWithObjectTask(Quester plugin, String objectName, String[] options, WorldPoint objectLoc, BooleanSupplier successCondition){
        this.objectName = objectName;
        this.objectLoc = objectLoc;
        this.plugin = plugin;
        this.options = options;
        this.successCondition = successCondition;
    }

    public String name() {
        return "Interact with " + this.objectName;
    }

    public WorldPoint location() {
        return this.objectLoc;
    }

    public boolean execute() {
        if (interactAttempts >= 5){
            log.info("Failed interact with object task. Too many attempts.");
            this.failed = true;
            return false;
        }

        PTileObject object = PObjects.findObject(
                Filters.Objects.nameEquals("Coffin")
                .and(Filters.Objects.actionsContains(options)));
        if (object == null || (!walkedToDestination && !Reachable.getMap().canReach(new RSTile(location())))) {
            if (!walkedToDestination && DaxWalker.walkTo(new RSTile(location()))){
                walkedToDestination = true;
                PUtils.waitCondition(PUtils.random(2500, 3100), () -> !PPlayer.isMoving());
                log.info("Walked to object");
                return true;
            } else {
                this.failed = true;
                log.info("Unable to walk to object!");
                return false;
            }
        } else {
            if (!PInteraction.tileObject(object, options)) {
                log.info("Unable to intaract with object! Looking for options: " + String.join(", ", options));
                this.failed = true;
                return false;
            } else {
                PUtils.waitCondition(PUtils.random(800, 1400), () -> PPlayer.isMoving());
                int distance = (int)Math.round(PPlayer.distanceTo(object.getWorldLocation()));
                int multiplier = PPlayer.isRunEnabled() ? 400 : 800;
                int timeout = distance * multiplier + (int)PUtils.randomNormal(1900, 2800);
                PUtils.waitCondition(timeout, () -> !PPlayer.isMoving());
                if (!PUtils.waitCondition(PUtils.random(3100, 3800), successCondition)){
                    interactAttempts++;
                    log.info("Timed out while waiting interaction success!");
                    return true;
                } else {
                    this.isCompleted = true;
                    return true;
                }
            }
        }
    };

    public boolean condition() {
        return !isCompleted;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public boolean isFailed(){
        return this.failed;
    }

    public int getDistance(){
        WorldPoint playerLoc = PPlayer.getWorldLocation();
        Point3D playerLocPoint = new Point3D(playerLoc.getX(), playerLoc.getY(), playerLoc.getPlane());
        WorldPoint taskLoc = location();
        Point3D taskLocPoint = new Point3D(taskLoc.getX(), taskLoc.getY(), taskLoc.getPlane());
        PathResult path = WebWalkerServerApi.getInstance().getPath(playerLocPoint, taskLocPoint, PlayerDetails.generate());
        if (path.getPathStatus() == PathStatus.SUCCESS) {
            return path.getCost();
        } else {
            return Integer.MAX_VALUE;
        }
    };
}