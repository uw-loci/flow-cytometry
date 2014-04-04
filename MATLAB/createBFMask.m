
function [ brightfield_mask, particlePixelAreas ] = createBFMask( rawImage, minParticleSize, maxParticleSize, gaussianSigma)

    accuracy = 0.01;
    kRadius = ceil(gaussianSigma*sqrt(-2*log(accuracy))+1);
    H = fspecial('gaussian',[kRadius kRadius], 2.2);
    gaussblurred = imfilter(rawImage,H);

    level = isodata(gaussblurred);
    brightfield_mask = ~im2bw(gaussblurred,level);

    brightfield_mask = imclearborder(brightfield_mask);
    brightfield_mask = bwareaopen(brightfield_mask, minParticleSize);
    
    CC = bwconncomp(brightfield_mask);
    particlePixelAreas = cellfun(@numel,CC.PixelIdxList);
    
    idxToKeep = CC.PixelIdxList(particlePixelAreas < maxParticleSize);
    idxToKeep = vertcat(idxToKeep{:});
    brightfield_mask = false(size(brightfield_mask));
    brightfield_mask(idxToKeep) = true;

end

