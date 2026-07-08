package com.renderoptimiser.utils.screenshots

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage

/**
 * Wraps a [BufferedImage] so it can be placed on the system clipboard as an image
 * ([DataFlavor.imageFlavor]). Pasting into Discord / most chat apps yields the image itself.
 */
class ImageTransferable(private val image: BufferedImage): Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.imageFlavor.equals(flavor)

    @Throws(UnsupportedFlavorException::class)
    override fun getTransferData(flavor: DataFlavor): Any {
        if (! DataFlavor.imageFlavor.equals(flavor)) throw UnsupportedFlavorException(flavor)
        return image
    }
}
