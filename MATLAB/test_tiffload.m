clear;
% clc;

minParticleSize = 1000;
maxParticleSize = 2300;
gaussianSigma = 2.2;
thresholdMin = 30;
ratioUpperLimit = 0.9;
ratioLowerLimit = 0.001;

captures = 0;

fnameBF = 'D:\Ajeet\Desktop\FLOW\s1-bf-small.tif';
infoBF = imfinfo(fnameBF);
bfstack = [];
numImagesBF = length(infoBF);

fnameIn = 'D:\Ajeet\Desktop\FLOW\s1-int-small.tif';
infoIn = imfinfo(fnameIn);
intstack = [];
numImagesIn = length(infoIn);

if numImagesBF ~= numImagesIn
    break;
end

for k=1:numImagesBF 
    image = imread(fnameBF,k);
    bfstack(:,:,k) =  uint8(image);
end;

for k=1:numImagesIn
    image = imread(fnameIn,k);
    intstack(:,:,k) =  uint8(image);
end;

for imageIndex = 10:20 %0:numImagesBF
    
    %create brightfield mask 

    [ brightfield_mask, particlesPixelCount ] = createBFMask( uint8(bfstack(:,:,imageIndex)), minParticleSize, maxParticleSize, gaussianSigma);
    figure(1)
    %imshow(brightfield_mask)

    %threshold intensity image
    intensity_image = uint8(intstack(:,:,imageIndex)) > thresholdMin;

    %multiply mask with intensity image to find intensity INSIDE particles
    intensity_masked = immultiply(brightfield_mask, intensity_image);

    %(find ratios)
        %compute ratio
        ratioValues = computeRatio( brightfield_mask, intensity_masked );

    %gate particle
    if(~isempty(ratioValues))
        for index = 1:size(ratioValues)
            if (ratioValues(index) < ratioUpperLimit) && (ratioValues(index) > ratioLowerLimit)
                captures = captures+1;
                break;
            end
        end
    end

    figure(8)
    subplot(2,2,1)
    imshow(uint8(bfstack(:,:,imageIndex)));
    subplot(2,2,2)
    imshow(brightfield_mask);
    subplot(2,2,3)
    imshow(uint8(intstack(:,:,imageIndex)));
    subplot(2,2,4)
    imshow(intensity_masked);
    
end


