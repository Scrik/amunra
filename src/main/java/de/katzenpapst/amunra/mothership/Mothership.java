package de.katzenpapst.amunra.mothership;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import de.katzenpapst.amunra.AmunRa;
import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.api.galaxies.GalaxyRegistry;
import micdoodle8.mods.galacticraft.api.galaxies.Moon;
import micdoodle8.mods.galacticraft.api.galaxies.Planet;
import micdoodle8.mods.galacticraft.api.galaxies.Satellite;
import micdoodle8.mods.galacticraft.api.galaxies.SolarSystem;
import micdoodle8.mods.galacticraft.api.galaxies.Star;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldProvider;
import scala.tools.nsc.backend.icode.Primitives.ArrayLength;

public class Mothership extends CelestialBody {

    protected String owner;

    protected String msName = "";

    protected CelestialBody currentParent;

    protected int travelTimeRemaining;

    protected boolean inTransit = false;

    protected int mothershipId;


    // the backslash should definitely not be valid for unlocalizedName
    public static final String nameSeparator = "\\";

    public Mothership(int id, String owner) {
        super("mothership_"+id);
        mothershipId = id;
        this.owner = owner;
        this.setBodyIcon(new ResourceLocation(AmunRa.ASSETPREFIX, "textures/gui/celestialbodies/mothership.png"));
        this.setRelativeOrbitTime(5);
    }

    public boolean setParent(CelestialBody parent) {
        if(parent instanceof Satellite) {
            return false;
        }

        currentParent = parent;
        inTransit = false;
        travelTimeRemaining = 0;

        return true;
    }

    @Override
    public boolean getReachable()
    {
        return this.isReachable;
    }

    @Override
    public void setUnreachable()
    {
        // noop
        this.isReachable = true;
    }

    @Override
    public String getLocalizedName()
    {
        if(msName.isEmpty()) {
            msName = String.format(StatCollector.translateToLocal("mothership.default.name"), mothershipId);
        }
        return msName;
    }


    public boolean isInTransit() {
        return inTransit;
    }

    /**
     * Returns the parent, if stationary
     * @return
     */
    public CelestialBody getParent() {
        if(this.inTransit) {
            return null;
        }
        return currentParent;
    }

    /**
     * Returns the destination or the parent if stationary
     * @return
     */
    public CelestialBody getDestination() {
        return currentParent;
    }

    /**
     * Only do stuff regarding this object itself, send the packets and stuff at someplace else
     *
     * @param target
     * @return
     */
    public boolean startTransit(CelestialBody target) {
        if(!canBeOrbited(target)) {
            return false;
        }

        FMLLog.info("Mothership %d will begin transit to %s", this.getID(), target.getName());

        // allow change of route in mid-transit, too
        this.inTransit = true;
        this.travelTimeRemaining = this.getTravelTimeTo(target);
        this.currentParent = target;
        // mark the MS data dirty here?


        return true;
    }

    public void endTransit()
    {
        FMLLog.info("Mothership %d finished transit", this.getID());
        this.travelTimeRemaining = 0;
        this.inTransit = false;
    }

    public int getTravelTimeTo(CelestialBody target) {
        return 240; // for now
    }

    public String getOwner() {
        return owner;
    }

    @Override
    public int getID() {
        return mothershipId;
    }

    @Override
    public String getUnlocalizedNamePrefix() {
        return "mothership";
    }

    public static boolean canBeOrbited(CelestialBody body) {
        return (body instanceof Planet) || (body instanceof Moon) || (body instanceof Star);
    }

    protected static String getSystemMainStarName(SolarSystem sys) {
        return sys.getName();/*+
                nameSeparator+
                sys.getMainStar().getName();*/
    }

    protected static String getPlanetName(Planet planet) {
        return getSystemMainStarName(planet.getParentSolarSystem())+
                nameSeparator+
                planet.getName();
    }

    protected static String getMoonName(Moon moon) {
        return getPlanetName(moon.getParentPlanet())+
                nameSeparator+
                moon.getName();
    }

    public CelestialBody setDimensionInfo(int dimID)
    {
        return this.setDimensionInfo(dimID, MothershipWorldProvider.class);
    }

    public static String getOrbitableBodyName(CelestialBody body) {

        // now try solarSystem\planet\moon format


        if(body instanceof Star) {
            return getSystemMainStarName(((Star)body).getParentSolarSystem());
        }

        if(body instanceof Planet) {
            return getPlanetName((Planet) body);
        }

        if(body instanceof Moon) {
            return getMoonName((Moon)body);
        }

        throw new RuntimeException("Invalid celestialbody for "+body.getName());
    }

    /**
     * Finds mothership-able bodies by "url", aka "solarSystem\planet\moon"
     *
     * @param bodyName
     * @return
     */
    public static CelestialBody findBodyByNamePath(String bodyName) {

        SolarSystem curSys = null;
        CelestialBody body = null;
        CelestialBody moon = null;

        String[] parts = bodyName.split(Pattern.quote(nameSeparator));

        for(int i=0;i<parts.length;i++) {
            switch(i) {
            case 0:
                //
                curSys = GalaxyRegistry.getRegisteredSolarSystems().get(parts[i]);
                body = curSys.getMainStar();
                break;
            case 1:
                body = GalaxyRegistry.getRegisteredPlanets().get(parts[i]);
                // sanity check
                if(!((Planet)body).getParentSolarSystem().equals(curSys)) {
                    throw new RuntimeException("Planet "+body.getName()+" is not in "+bodyName);
                }
                break;
            case 2:
                moon = GalaxyRegistry.getRegisteredMoons().get(parts[i]);
                // sanity checks
                if(!((Moon)moon).getParentPlanet().equals(body)) {
                    throw new RuntimeException("Moon "+moon.getName()+" is not in "+bodyName);
                }
                // at this point, we are done anyway
                return moon;
            }
        }
        if(body == null) {
            throw new RuntimeException("Could not find body for "+bodyName);
        }
        return body;
    }

    /**
     * Finds mothership-able bodies by galacticraft body name, aka the english name...
     *
     * @param bodyName
     * @return
     */
    public static CelestialBody findBodyByGCBodyName(String bodyName) {
        Collection<SolarSystem> sysList = GalaxyRegistry.getRegisteredSolarSystems().values();
        CelestialBody body;
        for(SolarSystem sys: sysList) {
            body = sys.getMainStar();
            if(body.getName() == bodyName ) {
                return body;
            }
        }
        body = GalaxyRegistry.getRegisteredPlanets().get(bodyName);
        if(body != null) {
            return body;
        }

        body = GalaxyRegistry.getRegisteredMoons().get(bodyName);
        return body;
    }

    /**
     * Automatically
     * @param str
     * @return
     */
    public static CelestialBody findBodyByString(String str) {
        if(str.contains(nameSeparator)) {
            return findBodyByNamePath(str);
        }
        return findBodyByGCBodyName(str);
    }

    public static Mothership createFromNBT(NBTTagCompound data) {
        if(!data.hasKey("id") || !data.hasKey("owner")) {
            throw new RuntimeException("Invalid Mothership!");
        }
        int id = data.getInteger("id");
        String owner = data.getString("owner");

        Mothership result = new Mothership(id, owner);

        // these must always be set, a mothership is invalid without

        String parentId = data.getString("parentName");
        CelestialBody foundParent = findBodyByNamePath(parentId);

        result.currentParent = foundParent;
        result.inTransit = data.getBoolean("inTransit");
        result.travelTimeRemaining = data.getInteger("travelTimeRemaining");
        result.msName = data.getString("name");
        result.setDimensionInfo(data.getInteger("dim"));
        result.isReachable = true;


        return result;
    }

    public void writeToNBT(NBTTagCompound data) {
        data.setString("owner", this.owner);
        data.setInteger("id", this.mothershipId);
        data.setInteger("dim", this.dimensionID);
        data.setString("name", this.msName);

        String parentId = getOrbitableBodyName(this.currentParent);

        data.setString("parentName", parentId);

        data.setBoolean("inTransit", this.inTransit);
        data.setInteger("travelTimeRemaining", this.travelTimeRemaining);

    }

    public int getRemainingTravelTime() {
        return 0;
    }

    public int modRemainingTravelTime(int mod) {
        this.travelTimeRemaining += mod;
        return this.travelTimeRemaining;
    }

    public void setRemainingTravelTime(int set) {
        this.travelTimeRemaining = set;
    }

}
