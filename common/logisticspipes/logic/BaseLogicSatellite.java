/**
 * Copyright (c) Krapht, 2011
 * 
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package logisticspipes.logic;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.network.GuiIDs;
import logisticspipes.network.NetworkConstants;
import logisticspipes.network.packets.PacketCoordinates;
import logisticspipes.network.packets.PacketPipeInteger;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.RequestManager;
import logisticspipes.utils.ItemIdentifierStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import buildcraft.core.network.TileNetworkData;
import cpw.mods.fml.common.network.Player;

public class BaseLogicSatellite extends BaseRoutingLogic implements IRequireReliableTransport {

	public static HashSet<BaseLogicSatellite> AllSatellites = new HashSet<BaseLogicSatellite>();

	// called only on server shutdown
	public static void cleanup() {
		AllSatellites.clear();
	}
	protected final LinkedList<ItemIdentifierStack> _lostItems = new LinkedList<ItemIdentifierStack>();

	@TileNetworkData
	public int satelliteId;

	public BaseLogicSatellite() {
		throttleTime = 40;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		satelliteId = nbttagcompound.getInteger("satelliteid");
		ensureAllSatelliteStatus();
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		nbttagcompound.setInteger("satelliteid", satelliteId);
		super.writeToNBT(nbttagcompound);
	}

	protected int findId(int increment) {
		if(MainProxy.isClient()) return satelliteId;
		int potentialId = satelliteId;
		boolean conflict = true;
		while (conflict) {
			potentialId += increment;
			if (potentialId < 0) {
				return 0;
			}
			conflict = false;
			for (final BaseLogicSatellite sat : AllSatellites) {
				if (sat.satelliteId == potentialId) {
					conflict = true;
					break;
				}
			}
		}
		return potentialId;
	}

	protected void ensureAllSatelliteStatus() {
		if(MainProxy.isClient()) return;
		if (satelliteId == 0 && AllSatellites.contains(this)) {
			AllSatellites.remove(this);
		}
		if (satelliteId != 0 && !AllSatellites.contains(this)) {
			AllSatellites.add(this);
		}
	}

	public void setNextId(EntityPlayer player) {
		satelliteId = findId(1);
		ensureAllSatelliteStatus();
		if (MainProxy.isClient(player.worldObj)) {
			final PacketCoordinates packet = new PacketCoordinates(NetworkConstants.SATELLITE_PIPE_NEXT, xCoord, yCoord, zCoord);
			MainProxy.sendPacketToServer(packet.getPacket());
		} else {
			final PacketPipeInteger packet = new PacketPipeInteger(NetworkConstants.SATELLITE_PIPE_SATELLITE_ID, xCoord, yCoord, zCoord, satelliteId);
			MainProxy.sendPacketToPlayer(packet.getPacket(), (Player)player);
		}
		updateWatchers();
	}

	public void setPrevId(EntityPlayer player) {
		satelliteId = findId(-1);
		ensureAllSatelliteStatus();
		if (MainProxy.isClient(player.worldObj)) {
			final PacketCoordinates packet = new PacketCoordinates(NetworkConstants.SATELLITE_PIPE_PREV, xCoord, yCoord, zCoord);
			MainProxy.sendPacketToServer(packet.getPacket());
		} else {
			final PacketPipeInteger packet = new PacketPipeInteger(NetworkConstants.SATELLITE_PIPE_SATELLITE_ID, xCoord, yCoord, zCoord, satelliteId);
			MainProxy.sendPacketToPlayer(packet.getPacket(),(Player) player);
		}
		updateWatchers();
	}

	private void updateWatchers() {
		for(EntityPlayer player : ((PipeItemsSatelliteLogistics)this.container.pipe).localModeWatchers) {
			final PacketPipeInteger packet = new PacketPipeInteger(NetworkConstants.SATELLITE_PIPE_SATELLITE_ID, xCoord, yCoord, zCoord, satelliteId);
			MainProxy.sendPacketToPlayer(packet.getPacket(),(Player) player);
		}
	}

	@Override
	public void destroy() {
		if(MainProxy.isClient()) return;
		if (AllSatellites.contains(this)) {
			AllSatellites.remove(this);
		}
	}

	@Override
	public void onWrenchClicked(EntityPlayer entityplayer) {
		if (MainProxy.isServer(entityplayer.worldObj)) {
			// Send the satellite id when opening gui
			final PacketPipeInteger packet = new PacketPipeInteger(NetworkConstants.SATELLITE_PIPE_SATELLITE_ID, xCoord, yCoord, zCoord, satelliteId);
			MainProxy.sendPacketToPlayer(packet.getPacket(), (Player)entityplayer);
			entityplayer.openGui(LogisticsPipes.instance, GuiIDs.GUI_SatelitePipe_ID, worldObj, xCoord, yCoord, zCoord);

		}
	}

	@Override
	public void throttledUpdateEntity() {
		super.throttledUpdateEntity();
		if (_lostItems.isEmpty()) {
			return;
		}
		final Iterator<ItemIdentifierStack> iterator = _lostItems.iterator();
		while (iterator.hasNext()) {
			ItemIdentifierStack stack = iterator.next();
			int received = RequestManager.requestPartial(stack, (CoreRoutedPipe) container.pipe);
			if(received > 0) {
				if(received == stack.stackSize) {
					iterator.remove();
				} else {
					stack.stackSize -= received;
				}
			}
		}
	}

	@Override
	public void itemLost(ItemIdentifierStack item) {
		_lostItems.add(item);
	}

	@Override
	public void itemArrived(ItemIdentifierStack item) {
	}

	public void setSatelliteId(int integer) {
		satelliteId = integer;
	}
}
