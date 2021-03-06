package ch.epfl.biop.bdv.bioformats.imageloader;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.bioformats.*;
import loci.formats.*;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.*;
import net.imglib2.Volatile;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class BioFormatsImageLoader implements ViewerImgLoader,MultiResolutionImgLoader {

    public List<File> files;

    final AbstractSequenceDescription<?, ?, ?> sequenceDescription;

    Consumer<String> log = s -> System.out.println(s);

    Map<Integer, FileSerieChannel> viewSetupToBFFileSerieChannel = new HashMap<>();

    int viewSetupCounter = 0;

    Map<Integer,Map<Integer,Supplier<NumericType>>> tTypeGetter = new HashMap<>();

    Map<Integer,Map<Integer,Supplier<Volatile>>> vTypeGetter = new HashMap<>();

    HashMap<Integer, BFViewerImgLoader> imgLoaders = new HashMap<>();

    protected VolatileGlobalCellCache cache;

    public BioFormatsImageLoader(List<File> files, final AbstractSequenceDescription<?, ?, ?> sequenceDescription) {
        this.files = files;
        this.sequenceDescription = sequenceDescription;
        IFormatReader readerIdx = new ImageReader();

        readerIdx.setFlattenedResolutions(false);
        Memoizer memo = new Memoizer( readerIdx );

        final IMetadata omeMetaOmeXml = MetadataTools.createOMEXMLMetadata();
        memo.setMetadataStore(omeMetaOmeXml);

        IntStream filesIdxStream = IntStream.range(0, files.size());
        if ((sequenceDescription!=null)) {
            filesIdxStream.forEach(iF -> {
                try {
                        File f = files.get(iF);
                        memo.setId(f.getAbsolutePath());

                        tTypeGetter.put(iF,new HashMap<>());
                        vTypeGetter.put(iF,new HashMap<>());

                        final IFormatReader reader = memo;

                        log.accept("Number of Series : " + reader.getSeriesCount());
                        final IMetadata omeMeta = (IMetadata) reader.getMetadataStore();

                        // -------------------------- SETUPS For each Series : one per timepoint and one per channel

                        IntStream series = IntStream.range(0, reader.getSeriesCount());

                        series.forEach(iSerie -> {
                            reader.setSeries(iSerie);
                            // One serie = one Tile
                            // ---------- Serie >
                            // ---------- Serie > Timepoints
                            log.accept("\t Serie " + iSerie + " Number of timesteps = " + omeMeta.getPixelsSizeT(iSerie).getNumberValue().intValue());
                            // ---------- Serie > Channels
                            log.accept("\t Serie " + iSerie + " Number of channels = " + omeMeta.getChannelCount(iSerie));
                            // Properties of the serie
                            IntStream channels = IntStream.range(0, omeMeta.getChannelCount(iSerie));
                            // Register Setups (one per channel and one per timepoint)
                            channels.forEach(
                                    iCh -> {
                                        IntStream timepoints = IntStream.range(0, omeMeta.getPixelsSizeT(iSerie).getNumberValue().intValue());
                                        FileSerieChannel fsc = new FileSerieChannel(iF, iSerie, iCh);
                                        timepoints.forEach(
                                                iTp -> {
                                                    viewSetupToBFFileSerieChannel.put(viewSetupCounter,fsc);
                                                    viewSetupCounter++;
                                                });
                                    });

                            try {
                                BioFormatsHelper h = new BioFormatsHelper(reader, iSerie);
                                if (h.is24bitsRGB) {
                                    tTypeGetter.get(iF).put(iSerie, () -> new ARGBType());
                                    vTypeGetter.get(iF).put(iSerie, () -> new VolatileARGBType());
                                } else {
                                    if (h.is8bits) {
                                        tTypeGetter.get(iF).put(iSerie, () -> new UnsignedByteType());
                                        vTypeGetter.get(iF).put(iSerie, () -> new VolatileUnsignedByteType());
                                    }
                                    if (h.is16bits) {
                                        tTypeGetter.get(iF).put(iSerie, () -> new UnsignedShortType());
                                        vTypeGetter.get(iF).put(iSerie, () -> new VolatileUnsignedShortType());
                                    }
                                    if (h.is32bits) {
                                        tTypeGetter.get(iF).put(iSerie, () -> new UnsignedIntType());
                                        vTypeGetter.get(iF).put(iSerie, () -> new VolatileUnsignedIntType());
                                    }
                                    if (h.isFloat32bits) {
                                        tTypeGetter.get(iF).put(iSerie, () -> new FloatType());
                                        vTypeGetter.get(iF).put(iSerie, () -> new VolatileFloatType());
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        });
                        reader.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        // NOT CORRECTLY IMPLEMENTED YET
        final BlockingFetchQueues<Callable<?>> queue = new BlockingFetchQueues<>(1);
        cache = new VolatileGlobalCellCache(queue);
    }

    public BFViewerImgLoader getSetupImgLoader(int setupId) {
        if (imgLoaders.containsKey(setupId)) {
            return imgLoaders.get(setupId);
        } else {
            int iF = viewSetupToBFFileSerieChannel.get(setupId).iFile;
            int iS = viewSetupToBFFileSerieChannel.get(setupId).iSerie;
            int iC = viewSetupToBFFileSerieChannel.get(setupId).iChannel;
            BFViewerImgLoader imgL = new BFViewerImgLoader(
                    files.get(iF),
                    iS,
                    iC,
                    false,
                    false,
                    true,
                    -1,
                    -1,
                    -1,
                    tTypeGetter.get(iF).get(iS),
                    vTypeGetter.get(iF).get(iS)
            );
            imgLoaders.put(setupId,imgL);
            return imgL;
        }
    }

    @Override
    public CacheControl getCacheControl() {
        return cache;
    }

}
