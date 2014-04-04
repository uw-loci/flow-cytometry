
function [ ratioValues ] = computeRatio( brightfield_mask, intensity_masked )

    CC = bwconncomp(brightfield_mask);
    %particlePixelAreas = cellfun(@numel,CC.PixelIdxList);
    roi = cellfun(@numel,CC.PixelIdxList);       
    ratioValues = zeros(size(roi));
    if isempty(roi)
        return;
    end

    for index = 1:size(roi)
        idxToKeep = CC.PixelIdxList(roi == roi(index));
        idxToKeep = vertcat(idxToKeep{:});
        tempMask = false(size(brightfield_mask));
        tempMask(idxToKeep) = true;

        isolatedRoiIntensity = immultiply(tempMask, intensity_masked);
        ratioValues(index) = sum(sum(isolatedRoiIntensity)) / roi(index);

    end
    


end

